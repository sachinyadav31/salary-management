package com.acme.salary.bonus;

import com.acme.salary.auth.CurrentUser;
import com.acme.salary.common.ConflictException;
import com.acme.salary.employee.EmployeeCommands.CreateEmployee;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.query.EmployeeQueryRepository;
import com.acme.salary.reference.JobLevel;
import com.acme.salary.reference.ReferenceConfig;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({BonusService.class, EmployeeService.class, SalaryService.class, EmployeeQueryRepository.class, ReferenceConfig.class,
        CurrentUser.class, BonusServiceTest.FixedClock.class})
class BonusServiceTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired BonusService bonuses;
    @Autowired EmployeeService employees;
    @Autowired EmployeeQueryRepository queries;
    @Autowired EntityManager em;

    long id;

    @BeforeEach
    void hire() {
        id = employees.create(new CreateEmployee("Ada", "Lovelace", "ada@bonus.test", "GB", 1, "Engineer", JobLevel.L3,
                LocalDate.of(2024, 1, 15), new BigDecimal("80000")));
        em.flush();
    }

    @Test
    void recordsAwardsNewestFirstWithoutChangingBaseSalary() {
        bonuses.award(id, new BonusService.AwardBonus(BonusType.PERFORMANCE, new BigDecimal("9600"), LocalDate.of(2026, 4, 1), null));
        bonuses.award(id, new BonusService.AwardBonus(BonusType.SPOT, new BigDecimal("1500"), LocalDate.of(2026, 9, 1), "Launch hero"));
        bonuses.award(id, new BonusService.AwardBonus(BonusType.SIGN_ON, new BigDecimal("5000"), LocalDate.of(2024, 1, 15), null));
        em.flush();

        assertThat(bonuses.bonuses(id)).extracting(b -> b.type() + " " + b.amount().amount())
                .containsExactly("SPOT 1500.00", "PERFORMANCE 9600.00", "SIGN_ON 5000.00");
        var detail = queries.findDetail(id).orElseThrow();
        assertThat(detail.summary().salary().amount()).isEqualTo("80000.00");
        assertThat(detail.bonusesLast12Months().amount()).as("sign-on bonus is older than 12 months").isEqualTo("11100.00");
    }

    @Test
    void inactiveEmployeesCannotReceiveBonuses() {
        employees.changeStatus(id, EmployeeStatus.INACTIVE, 0);
        em.flush();

        assertThatThrownBy(() -> bonuses.award(id, new BonusService.AwardBonus(BonusType.RETENTION, new BigDecimal("100"),
                LocalDate.of(2026, 10, 1), null))).isInstanceOf(ConflictException.class);
    }
}
