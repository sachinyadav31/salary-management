package com.acme.salary.bulk;

import com.acme.salary.auth.CurrentUser;
import com.acme.salary.bonus.BonusService;
import com.acme.salary.bonus.BonusType;
import com.acme.salary.bulk.BulkPlanner.Kind;
import com.acme.salary.bulk.BulkPlanner.Request;
import com.acme.salary.employee.EmployeeCommands.CreateEmployee;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.query.EmployeeQueryRepository;
import com.acme.salary.employee.query.EmployeeSearchCriteria;
import com.acme.salary.reference.JobLevel;
import com.acme.salary.reference.ReferenceConfig;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.salary.SalaryService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({BulkService.class, BonusService.class, EmployeeService.class, SalaryService.class, EmployeeQueryRepository.class,
        ReferenceConfig.class, CurrentUser.class, BulkServiceTest.FixedClock.class})
class BulkServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired BulkService bulk;
    @Autowired EmployeeService employees;
    @Autowired SalaryService salaries;
    @Autowired BonusService bonuses;
    @Autowired EmployeeQueryRepository queries;
    @Autowired EntityManager em;

    long ada, alan, grace;
    final EmployeeSearchCriteria group = EmployeeSearchCriteria.builder().search("@bulk.test").build();

    @BeforeEach
    void hire() {
        ada = hire("Ada", "ada@bulk.test", "80000");
        alan = hire("Alan", "alan@bulk.test", "60000");
        grace = hire("Grace", "grace@bulk.test", "70000");
        employees.changeStatus(grace, EmployeeStatus.INACTIVE, 0);
        em.flush();
        em.clear();
    }

    long hire(String first, String email, String salary) {
        return employees.create(new CreateEmployee(first, "Test", email, "GB", 1, "Engineer", JobLevel.L3,
                LocalDate.of(2024, 1, 15), new BigDecimal(salary)));
    }

    static Request merit(String percent, LocalDate date) {
        return new Request(Kind.RAISE, new BigDecimal(percent), null, date, SalaryChangeReason.MERIT, null, "FY27 merit");
    }

    @Test
    void previewShowsWhatWouldBeSavedWithoutSavingIt() {
        var preview = bulk.preview(group, merit("5", TODAY));

        assertThat(preview.matched()).isEqualTo(3);
        assertThat(preview.affected()).isEqualTo(2);
        assertThat(preview.rows()).extracting(r -> r.from().amount() + " → " + r.to().amount())
                .containsExactlyInAnyOrder("80000.00 → 84000.00", "60000.00 → 63000.00");
        assertThat(preview.totalsByCurrency()).extracting(m -> m.currency() + " " + m.amount()).containsExactly("GBP 7000.00");
        assertThat(preview.totalUsd().amount()).isEqualTo("8890.00"); // 7,000 GBP × 1.27
        assertThat(preview.skipped()).extracting(g -> g.reason() + " " + g.count()).containsExactly("Inactive 1");
        assertThat(salaries.timeline(ada)).hasSize(1);
    }

    @Test
    void applyRaisesEveryoneInOneGoBumpsVersionsAndIsIdempotent() {
        UUID requestId = UUID.randomUUID();

        var result = bulk.apply(group, merit("5", TODAY), requestId);
        em.flush();
        em.clear();
        var again = bulk.apply(group, merit("5", TODAY), requestId);

        assertThat(result.applied()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(again.alreadyApplied()).isTrue();
        var detail = queries.findDetail(ada).orElseThrow();
        assertThat(detail.summary().salary().amount()).isEqualTo("84000.00");
        assertThat(detail.summary().salaryUsd().amount()).isEqualTo("106680.00");
        assertThat(salaries.timeline(ada)).hasSize(2).first()
                .satisfies(t -> assertThat(t.entry().reason()).isEqualTo(SalaryChangeReason.MERIT));
        assertThat(detail.version()).as("one version bump, so open forms see a conflict").isEqualTo(1);
    }

    @Test
    void aScheduledBulkRaiseLeavesTodaysSalaryForTheRolloverJob() {
        LocalDate april = LocalDate.of(2027, 4, 1);
        bulk.apply(group, merit("10", april), UUID.randomUUID());
        em.flush();
        em.clear();

        assertThat(queries.findDetail(alan).orElseThrow().summary().salary().amount()).isEqualTo("60000.00");
        assertThat(salaries.applyDueChanges(april)).isEqualTo(2);
        em.flush();
        em.clear();
        assertThat(queries.findDetail(alan).orElseThrow().summary().salary().amount()).isEqualTo("66000.00");
    }

    @Test
    void bulkBonusesAreAddedToTheLedger() {
        var result = bulk.apply(group, new Request(Kind.BONUS, new BigDecimal("10"), null, TODAY, null,
                BonusType.PERFORMANCE, null), UUID.randomUUID());
        em.flush();

        assertThat(result.applied()).isEqualTo(2);
        assertThat(bonuses.bonuses(ada)).extracting(b -> b.type() + " " + b.amount().amount())
                .containsExactly("PERFORMANCE 8000.00");
    }

    @Test
    void refusesToApplyWhenNobodyQualifies() {
        var nobody = EmployeeSearchCriteria.builder().search("grace@bulk.test").build();
        assertThatThrownBy(() -> bulk.apply(nobody, merit("3", TODAY), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Nobody");
    }
}
