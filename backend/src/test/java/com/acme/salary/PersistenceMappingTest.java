package com.acme.salary;

import com.acme.salary.common.Money;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.reference.CountryRepository;
import com.acme.salary.reference.DepartmentRepository;
import com.acme.salary.reference.FxRateRepository;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.salary.SalaryRecord;
import com.acme.salary.salary.SalaryRecordRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PersistenceMappingTest {

    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");

    @Autowired CountryRepository countries;
    @Autowired DepartmentRepository departments;
    @Autowired FxRateRepository fxRates;
    @Autowired EmployeeRepository employees;
    @Autowired SalaryRecordRepository salaryRecords;
    @Autowired EntityManager em;

    @Test
    void referenceDataIsLoadedByMigrations() {
        assertThat(countries.findAll()).hasSize(8);
        assertThat(departments.findAll()).hasSize(8);
        assertThat(fxRates.findById("JPY")).get()
                .satisfies(r -> assertThat(r.getUsdPerUnit()).isEqualByComparingTo("0.0067"));
    }

    @Test
    void employeeAndSalaryHistoryRoundTrip() {
        Employee saved = employees.saveAndFlush(new Employee("E-TEST-1", "Ada", "Lovelace", "ada@acme.test",
                countries.getReferenceById("JP"), departments.getReferenceById(1L), "Software Engineer",
                JobLevel.L3, LocalDate.of(2024, 4, 1), Money.ofMinor(9_000_000, "JPY"), Money.ofMinor(6_030_000, "USD"), NOW));
        salaryRecords.save(new SalaryRecord(saved, Money.ofMinor(8_000_000, "JPY"), LocalDate.of(2024, 4, 1),
                SalaryChangeReason.HIRE, null, NOW));
        salaryRecords.save(new SalaryRecord(saved, Money.ofMinor(9_000_000, "JPY"), LocalDate.of(2025, 4, 1),
                SalaryChangeReason.ANNUAL_REVIEW, "Strong year", NOW));
        em.flush();
        em.clear();

        Employee loaded = employees.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getCurrentSalary()).isEqualTo(Money.ofMinor(9_000_000, "JPY"));
        assertThat(loaded.getCurrentSalaryUsd()).isEqualTo(Money.ofMinor(6_030_000, "USD"));
        assertThat(loaded.getHireDate()).isEqualTo(LocalDate.of(2024, 4, 1));
        assertThat(loaded.getCountry().getCurrencyCode()).isEqualTo("JPY");

        assertThat(salaryRecords.findByEmployeeIdOrderByEffectiveDateDescIdDesc(saved.getId()))
                .extracting(SalaryRecord::getReason)
                .containsExactly(SalaryChangeReason.ANNUAL_REVIEW, SalaryChangeReason.HIRE);
    }

    @Test
    void datesAreStoredAsIsoText() {
        Employee saved = employees.saveAndFlush(new Employee("E-TEST-2", "Alan", "Turing", "alan@acme.test",
                countries.getReferenceById("GB"), departments.getReferenceById(1L), "Researcher",
                JobLevel.L5, LocalDate.of(2023, 1, 9), Money.ofMinor(9_500_000, "GBP"), Money.ofMinor(12_065_000, "USD"), NOW));

        Object stored = em.createNativeQuery("select hire_date from employee where id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();
        assertThat(stored).isEqualTo("2023-01-09");
    }
}
