package com.acme.salary.seed;

import com.acme.salary.common.Money;
import com.acme.salary.reference.JobLevel;
import com.acme.salary.salary.SalaryChangeReason;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeSeedGeneratorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 1);
    private static List<SeedEmployee> employees;

    @BeforeAll
    static void generateOnce() {
        employees = new EmployeeSeedGenerator(42, AS_OF).generate(10_000);
    }

    @Test
    void generatesTheRequestedNumberOfEmployees() {
        assertThat(employees).hasSize(10_000);
    }

    @Test
    void isDeterministicForTheSameSeed() {
        List<SeedEmployee> again = new EmployeeSeedGenerator(42, AS_OF).generate(500);
        assertThat(again).isEqualTo(employees.subList(0, 500));
    }

    @Test
    void differentSeedsProduceDifferentData() {
        List<SeedEmployee> other = new EmployeeSeedGenerator(7, AS_OF).generate(500);
        assertThat(other).isNotEqualTo(employees.subList(0, 500));
    }

    @Test
    void employeeCodesAndEmailsAreUnique() {
        assertThat(employees).extracting(SeedEmployee::employeeCode).doesNotHaveDuplicates();
        assertThat(employees).extracting(SeedEmployee::email).doesNotHaveDuplicates();
    }

    @Test
    void coversEveryCountryAndLevel() {
        assertThat(employees).extracting(SeedEmployee::countryCode).containsOnly(SeedCatalog.countryCodes());
        assertThat(employees.stream().map(SeedEmployee::countryCode).distinct()).hasSize(8);
        assertThat(employees.stream().map(SeedEmployee::level).distinct()).hasSize(JobLevel.values().length);
    }

    @Test
    void salaryHistoryIsConsistentWithCurrentSalary() {
        for (SeedEmployee e : employees) {
            List<SeedSalaryRecord> history = e.salaryHistory();
            assertThat(history).isNotEmpty().hasSizeLessThanOrEqualTo(3);
            assertThat(history.getFirst().reason()).isEqualTo(SalaryChangeReason.HIRE);
            assertThat(history.getFirst().effectiveDate()).isEqualTo(e.hireDate());
            assertThat(history).extracting(SeedSalaryRecord::effectiveDate).isSorted();
            assertThat(history.getLast().amount()).isEqualTo(e.currentSalary());
            assertThat(history).allSatisfy(r -> {
                assertThat(r.amount().isPositive()).isTrue();
                assertThat(r.effectiveDate()).isBeforeOrEqualTo(AS_OF);
                assertThat(r.amount().currencyCode()).isEqualTo(SeedCatalog.currencyOf(e.countryCode()));
            });
        }
    }

    @Test
    void currentSalariesStayWithinTheBandForCountryAndLevel() {
        for (SeedEmployee e : employees) {
            long expected = SeedCatalog.bandMidpointMinor(e.countryCode(), e.level());
            assertThat(e.currentSalary().minorUnits())
                    .as("%s %s %s", e.employeeCode(), e.countryCode(), e.level())
                    .isBetween(expected * 6 / 10, expected * 15 / 10);
        }
    }

    @Test
    void seniorLevelsAreBetterPaidOnAverageWithinACountry() {
        Map<JobLevel, Double> avgUs = employees.stream()
                .filter(e -> e.countryCode().equals("US"))
                .collect(Collectors.groupingBy(SeedEmployee::level,
                        Collectors.averagingLong(e -> e.currentSalary().minorUnits())));
        assertThat(avgUs.get(JobLevel.L1)).isLessThan(avgUs.get(JobLevel.L3));
        assertThat(avgUs.get(JobLevel.L3)).isLessThan(avgUs.get(JobLevel.L6));
    }

    @Test
    void zeroDecimalCurrenciesUseWholeUnits() {
        assertThat(employees).filteredOn(e -> e.countryCode().equals("JP"))
                .isNotEmpty()
                .extracting(SeedEmployee::currentSalary)
                .extracting(Money::currencyCode)
                .containsOnly("JPY");
    }
}
