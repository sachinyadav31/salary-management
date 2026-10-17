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
            assertThat(history).isNotEmpty().hasSizeLessThanOrEqualTo(14); // hire + 11 review cycles since 2016 + promotion + market
            assertThat(history.getFirst().reason()).isEqualTo(SalaryChangeReason.HIRE);
            assertThat(history.getFirst().effectiveDate()).isEqualTo(e.hireDate());
            assertThat(history).extracting(SeedSalaryRecord::effectiveDate).isSorted().doesNotHaveDuplicates();
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

    @Test
    void annualReviewsFollowOneCompanyCycleOnFirstApril() {
        var reviews = employees.stream().flatMap(e -> e.salaryHistory().stream()
                .filter(r -> r.reason() == SalaryChangeReason.ANNUAL_REVIEW)
                .map(r -> Map.entry(e, r))).toList();

        assertThat(reviews).isNotEmpty().allSatisfy(entry -> {
            LocalDate date = entry.getValue().effectiveDate();
            assertThat(date.getMonthValue()).isEqualTo(4);
            assertThat(date.getDayOfMonth()).isEqualTo(1);
            assertThat(date).as("employed at least 6 months before the review")
                    .isAfterOrEqualTo(entry.getKey().hireDate().plusMonths(6));
        });
    }

    @Test
    void promotionsAndAdjustmentsHappenOffCycleForAMinority() {
        long promoted = employees.stream().filter(e -> e.salaryHistory().stream()
                .anyMatch(r -> r.reason() == SalaryChangeReason.PROMOTION)).count();

        assertThat(promoted).isBetween(1_000L, 3_000L);
        assertThat(employees).allSatisfy(e -> assertThat(e.salaryHistory())
                .filteredOn(r -> r.reason() == SalaryChangeReason.PROMOTION || r.reason() == SalaryChangeReason.MARKET_ADJUSTMENT)
                .allSatisfy(r -> assertThat(r.effectiveDate().getMonthValue() == 4 && r.effectiveDate().getDayOfMonth() == 1).isFalse()));
    }

    @Test
    void raisesAreRealisticSizes() {
        for (SeedEmployee e : employees) {
            var history = e.salaryHistory();
            for (int i = 1; i < history.size(); i++) {
                double change = (double) history.get(i).amount().minorUnits() / history.get(i - 1).amount().minorUnits() - 1;
                assertThat(change).as("%s %s", e.employeeCode(), history.get(i).reason()).isBetween(-0.02, 0.17);
            }
        }
    }

    @Test
    void bonusesFollowHrPractice() {
        for (SeedEmployee e : employees) {
            long target = com.acme.salary.employee.VariableTarget.defaultFor(e.departmentName(), e.level())
                    .of(e.currentSalary()).minorUnits();
            assertThat(e.bonuses()).allSatisfy(b -> {
                assertThat(b.awardedOn()).isAfterOrEqualTo(e.hireDate()).isBeforeOrEqualTo(AS_OF);
                assertThat(b.amount().currencyCode()).isEqualTo(e.currentSalary().currencyCode());
                assertThat(b.amount().isPositive()).isTrue();
                switch (b.type()) {
                    case PERFORMANCE -> {
                        assertThat(b.awardedOn().getMonthValue()).isEqualTo(4);
                        assertThat(b.awardedOn().getDayOfMonth()).isEqualTo(1);
                        assertThat(b.awardedOn()).isAfterOrEqualTo(e.hireDate().plusYears(1));
                        assertThat(b.amount().minorUnits()).isLessThanOrEqualTo(Math.round(target * 1.4) + 100_000);
                    }
                    case SPOT -> assertThat(b.note()).isNotBlank();
                    case SIGN_ON -> assertThat(b.awardedOn()).isEqualTo(e.hireDate());
                    case RETENTION -> { }
                }
            });
        }
        long withSpot = employees.stream().filter(e -> e.bonuses().stream()
                .anyMatch(b -> b.type() == com.acme.salary.bonus.BonusType.SPOT)).count();
        assertThat(withSpot).isBetween(500L, 1_200L);
    }
}
