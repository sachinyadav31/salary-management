package com.acme.salary.employee;

import com.acme.salary.common.Money;
import com.acme.salary.reference.Country;
import com.acme.salary.reference.Department;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T09:00:00Z");
    private static final Country JAPAN = new Country("JP", "Japan", "JPY");
    private static final Department ENGINEERING = new Department(1L, "Engineering");

    @Test
    void newEmployeesAreActive() {
        assertThat(employee(Money.ofMinor(9_000_000, "JPY")).getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void salaryMustBeInTheCountryCurrency() {
        assertThatThrownBy(() -> employee(Money.ofMinor(9_000_000, "USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPY");
    }

    @Test
    void salaryMustBePositive() {
        assertThatThrownBy(() -> employee(Money.ofMinor(0, "JPY")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatingTheProfileTouchesUpdatedAtButNotCreatedAt() {
        Employee e = employee(Money.ofMinor(9_000_000, "JPY"));
        Instant later = CREATED.plusSeconds(3600);

        e.updateProfile("Akira", "Tanaka", "akira.tanaka@acme.example", ENGINEERING, "Staff Engineer", JobLevel.L4, later);

        assertThat(e.getLastName()).isEqualTo("Tanaka");
        assertThat(e.getLevel()).isEqualTo(JobLevel.L4);
        assertThat(e.getUpdatedAt()).isEqualTo(later);
        assertThat(e.getCreatedAt()).isEqualTo(CREATED);
    }

    @Test
    void canBeDeactivatedAndReactivated() {
        Employee e = employee(Money.ofMinor(9_000_000, "JPY"));

        e.changeStatus(EmployeeStatus.INACTIVE, CREATED);
        assertThat(e.getStatus()).isEqualTo(EmployeeStatus.INACTIVE);

        e.changeStatus(EmployeeStatus.ACTIVE, CREATED);
        assertThat(e.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    private static Employee employee(Money salary) {
        return new Employee("E1", "Akira", "Ito", "akira@acme.example", JAPAN, ENGINEERING, "Software Engineer",
                JobLevel.L3, LocalDate.of(2024, 4, 1), salary, Money.ofMinor(1, "USD"), CREATED);
    }
}
