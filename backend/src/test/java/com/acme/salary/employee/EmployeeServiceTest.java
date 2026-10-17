package com.acme.salary.employee;

import com.acme.salary.auth.CurrentUser;
import com.acme.salary.common.ConflictException;
import com.acme.salary.common.Money;
import com.acme.salary.common.NotFoundException;
import com.acme.salary.employee.EmployeeCommands.CreateEmployee;
import com.acme.salary.employee.EmployeeCommands.UpdateProfile;
import com.acme.salary.reference.JobLevel;
import com.acme.salary.reference.ReferenceConfig;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.salary.SalaryRecord;
import com.acme.salary.salary.SalaryRecordRepository;
import com.acme.salary.salary.SalaryService;
import jakarta.persistence.EntityManager;
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
@Import({EmployeeService.class, SalaryService.class, ReferenceConfig.class, CurrentUser.class, EmployeeServiceTest.FixedClock.class})
class EmployeeServiceTest {

    static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired EmployeeService service;
    @Autowired EmployeeRepository employees;
    @Autowired SalaryRecordRepository salaryRecords;
    @Autowired EntityManager em;

    @Test
    void createsEmployeeWithHireRecordAndUsdEquivalent() {
        long id = service.create(newHire("JP", "9000000"));

        Employee e = employees.findById(id).orElseThrow();
        assertThat(e.getEmployeeCode()).matches("E\\d{5}");
        assertThat(e.getCurrentSalary()).isEqualTo(Money.ofMinor(9_000_000, "JPY"));
        assertThat(e.getCurrentSalaryUsd()).isEqualTo(Money.ofMinor(6_030_000, "USD"));
        assertThat(e.getCreatedAt()).isEqualTo(NOW);
        assertThat(salaryRecords.findByEmployeeIdOrderByEffectiveDateDescIdDesc(id))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getReason()).isEqualTo(SalaryChangeReason.HIRE);
                    assertThat(r.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 11, 1));
                    assertThat(r.getAmount()).isEqualTo(Money.ofMinor(9_000_000, "JPY"));
                });
    }

    @Test
    void generatesSequentialEmployeeCodes() {
        Employee first = employees.findById(service.create(newHire("US", "100000", "a@acme.test"))).orElseThrow();
        Employee second = employees.findById(service.create(newHire("US", "100000", "b@acme.test"))).orElseThrow();

        long a = Long.parseLong(first.getEmployeeCode().substring(1));
        assertThat(second.getEmployeeCode()).isEqualTo("E%05d".formatted(a + 1));
    }

    @Test
    void normalisesEmailAndRejectsDuplicatesCaseInsensitively() {
        long id = service.create(newHire("US", "100000", "  Ada.Lovelace@ACME.test "));
        assertThat(employees.findById(id).orElseThrow().getEmail()).isEqualTo("ada.lovelace@acme.test");

        assertThatThrownBy(() -> service.create(newHire("GB", "50000", "ada.lovelace@acme.TEST")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsUnknownCountryAndDepartment() {
        assertThatThrownBy(() -> service.create(newHire("ZZ", "100")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("country");
        assertThatThrownBy(() -> service.create(new CreateEmployee("A", "B", "x@acme.test", "US", 999,
                "Engineer", JobLevel.L1, LocalDate.of(2026, 1, 1), new BigDecimal("100"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("department");
    }

    @Test
    void rejectsAmountsWithMoreDecimalsThanTheCurrencyAllows() {
        assertThatThrownBy(() -> service.create(newHire("JP", "9000000.50")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JPY");
    }

    @Test
    void updatesProfileAndBumpsVersion() {
        long id = service.create(newHire("IN", "1500000"));
        em.flush();

        service.updateProfile(id, new UpdateProfile("Priya", "Sharma", "priya.sharma@acme.test", 2,
                "Product Manager", JobLevel.L4, 0));
        em.flush();

        Employee e = employees.findById(id).orElseThrow();
        assertThat(e.getJobTitle()).isEqualTo("Product Manager");
        assertThat(e.getDepartment().getName()).isEqualTo("Product");
        assertThat(e.getVersion()).isEqualTo(1);
    }

    @Test
    void rejectsUpdateFromAStaleForm() {
        long id = service.create(newHire("IN", "1500000"));
        em.flush();
        UpdateProfile fromFirstTab = new UpdateProfile("Priya", "Sharma", "p1@acme.test", 1, "Engineer", JobLevel.L2, 0);
        UpdateProfile fromSecondTab = new UpdateProfile("Priya", "Sharma", "p2@acme.test", 1, "Engineer", JobLevel.L3, 0);

        service.updateProfile(id, fromFirstTab);
        em.flush();

        assertThatThrownBy(() -> service.updateProfile(id, fromSecondTab))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Reload");
    }

    @Test
    void updateRejectsAnotherEmployeesEmail() {
        service.create(newHire("US", "100000", "taken@acme.test"));
        long id = service.create(newHire("US", "100000", "mine@acme.test"));
        em.flush();

        assertThatThrownBy(() -> service.updateProfile(id,
                new UpdateProfile("A", "B", "TAKEN@acme.test", 1, "Engineer", JobLevel.L1, 0)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deactivatesAndReactivates() {
        long id = service.create(newHire("DE", "60000"));
        em.flush();

        service.changeStatus(id, EmployeeStatus.INACTIVE, 0);
        em.flush();
        assertThat(employees.findById(id).orElseThrow().getStatus()).isEqualTo(EmployeeStatus.INACTIVE);

        service.changeStatus(id, EmployeeStatus.ACTIVE, 1);
        em.flush();
        assertThat(employees.findById(id).orElseThrow().getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void unknownEmployeeIsNotFound() {
        assertThatThrownBy(() -> service.changeStatus(424242, EmployeeStatus.INACTIVE, 0))
                .isInstanceOf(NotFoundException.class);
    }

    private static CreateEmployee newHire(String country, String amount) {
        return newHire(country, amount, "new.hire@acme.test");
    }

    private static CreateEmployee newHire(String country, String amount, String email) {
        return new CreateEmployee(" Akira ", "Ito", email, country, 1, "Software Engineer", JobLevel.L3,
                LocalDate.of(2026, 11, 1), new BigDecimal(amount));
    }

    @Test
    void recordsCareerEventsForLevelTitleAndStatusChangesOnly() {
        long id = service.create(newHire("IN", "1500000"));
        em.flush();

        // Name and email edits are corrections, not career events.
        service.updateProfile(id, new UpdateProfile("Akira", "Ito-Sato", "akira.sato@acme.test", 1, "Software Engineer", JobLevel.L3, 0));
        em.flush();
        service.updateProfile(id, new UpdateProfile("Akira", "Ito-Sato", "akira.sato@acme.test", 1, "Senior Software Engineer", JobLevel.L4, 1));
        em.flush();
        service.changeStatus(id, EmployeeStatus.INACTIVE, 2);
        em.flush();

        // The test clock is fixed, so every event shares one instant and recording order applies;
        // in real use the newest edit comes first.
        assertThat(service.careerEvents(id)).extracting(e -> e.type() + ":" + e.from() + "->" + e.to()).containsExactly(
                "LEVEL_CHANGED:L3->L4",
                "TITLE_CHANGED:Software Engineer->Senior Software Engineer",
                "DEACTIVATED:null->null");
    }

    @Test
    void careerEventsOfUnknownEmployeeAreNotFound() {
        assertThatThrownBy(() -> service.careerEvents(424242)).isInstanceOf(NotFoundException.class);
    }
}
