package com.acme.salary.salary;

import com.acme.salary.common.ConflictException;
import com.acme.salary.common.Money;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeCommands.CreateEmployee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.reference.ReferenceConfig;
import com.acme.salary.salary.SalaryHistory.EntryStatus;
import com.acme.salary.salary.SalaryHistory.TimelineEntry;
import com.acme.salary.salary.SalaryService.RecordChange;
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

import static com.acme.salary.salary.SalaryChangeReason.ANNUAL_REVIEW;
import static com.acme.salary.salary.SalaryChangeReason.MARKET_ADJUSTMENT;
import static com.acme.salary.salary.SalaryChangeReason.PROMOTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({EmployeeService.class, SalaryService.class, ReferenceConfig.class, SalaryServiceTest.FixedClock.class})
class SalaryServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired EmployeeService employeeService;
    @Autowired SalaryService salaryService;
    @Autowired EmployeeRepository employees;
    @Autowired EntityManager em;

    long id;

    @BeforeEach
    void hireSomeoneInLondon() {
        id = employeeService.create(new CreateEmployee("Ada", "Lovelace", "ada@acme.test", "GB", 1,
                "Software Engineer", JobLevel.L3, LocalDate.of(2024, 1, 15), new BigDecimal("80000")));
        flushAndClear();
    }

    @Test
    void raiseInEffectTodayUpdatesCurrentSalaryUsdAndVersion() {
        salaryService.recordChange(id, change("88000", TODAY, PROMOTION, 0));
        flushAndClear();

        Employee ada = employees.findById(id).orElseThrow();
        assertThat(ada.getCurrentSalary()).isEqualTo(gbp("88000"));
        assertThat(ada.getCurrentSalaryUsd()).isEqualTo(Money.of(new BigDecimal("111760.00"), "USD")); // 88k * 1.27
        assertThat(ada.getVersion()).isEqualTo(1);
        assertThat(salaryService.timeline(id)).extracting(TimelineEntry::status)
                .containsExactly(EntryStatus.CURRENT, EntryStatus.PAST);
    }

    @Test
    void futureDatedChangeIsScheduledAndDoesNotChangeCurrentSalary() {
        salaryService.recordChange(id, change("90000", LocalDate.of(2027, 4, 1), ANNUAL_REVIEW, 0));
        flushAndClear();

        Employee ada = employees.findById(id).orElseThrow();
        assertThat(ada.getCurrentSalary()).isEqualTo(gbp("80000"));
        assertThat(salaryService.timeline(id).getFirst().status()).isEqualTo(EntryStatus.SCHEDULED);
    }

    @Test
    void backDatedChangeBeforeTheLatestOneDoesNotOverrideIt() {
        salaryService.recordChange(id, change("85000", LocalDate.of(2025, 1, 15), ANNUAL_REVIEW, 0));
        flushAndClear();
        salaryService.recordChange(id, change("82000", LocalDate.of(2024, 7, 1), MARKET_ADJUSTMENT, 1));
        flushAndClear();

        assertThat(employees.findById(id).orElseThrow().getCurrentSalary()).isEqualTo(gbp("85000"));
        assertThat(salaryService.timeline(id)).extracting(t -> t.entry().amount())
                .containsExactly(gbp("85000"), gbp("82000"), gbp("80000"));
    }

    @Test
    void rolloverAppliesChangesWhenTheyBecomeDueAndIsIdempotent() {
        salaryService.recordChange(id, change("90000", LocalDate.of(2027, 4, 1), ANNUAL_REVIEW, 0));
        flushAndClear();

        assertThat(salaryService.applyDueChanges(LocalDate.of(2027, 3, 31))).isZero();
        assertThat(salaryService.applyDueChanges(LocalDate.of(2027, 4, 1))).isEqualTo(1);
        flushAndClear();
        assertThat(salaryService.applyDueChanges(LocalDate.of(2027, 4, 1))).isZero();

        Employee ada = employees.findById(id).orElseThrow();
        assertThat(ada.getCurrentSalary()).isEqualTo(gbp("90000"));
        assertThat(ada.getCurrentSalaryUsd()).isEqualTo(Money.of(new BigDecimal("114300.00"), "USD"));
    }

    @Test
    void staleScreenIsRejected() {
        salaryService.recordChange(id, change("88000", TODAY, PROMOTION, 0));
        flushAndClear();

        assertThatThrownBy(() -> salaryService.recordChange(id, change("99000", TODAY.minusDays(1), PROMOTION, 0)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void inactiveEmployeesCannotGetRaises() {
        employeeService.changeStatus(id, EmployeeStatus.INACTIVE, 0);
        flushAndClear();

        assertThatThrownBy(() -> salaryService.recordChange(id, change("88000", TODAY, PROMOTION, 1)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("inactive");
    }

    @Test
    void domainRulesSurfaceAsInvalidRequests() {
        assertThatThrownBy(() -> salaryService.recordChange(id, change("88000", LocalDate.of(2023, 1, 1), PROMOTION, 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hire");
        assertThatThrownBy(() -> salaryService.recordChange(id, change("88000.001", TODAY, PROMOTION, 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("GBP");
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static RecordChange change(String amount, LocalDate date, SalaryChangeReason reason, long version) {
        return new RecordChange(new BigDecimal(amount), date, reason, null, version);
    }

    private static Money gbp(String amount) {
        return Money.of(new BigDecimal(amount), "GBP");
    }
}
