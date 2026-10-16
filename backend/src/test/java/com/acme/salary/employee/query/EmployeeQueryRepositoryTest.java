package com.acme.salary.employee.query;

import com.acme.salary.common.web.PageResponse;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.reference.ReferenceConfig;
import com.acme.salary.seed.DatabaseSeeder;
import com.acme.salary.seed.EmployeeSeedGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.acme.salary.common.ClockConfig;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({EmployeeQueryRepository.class, DatabaseSeeder.class, ReferenceConfig.class, ClockConfig.class})
class EmployeeQueryRepositoryTest {

    @Autowired EmployeeQueryRepository queries;
    @Autowired DatabaseSeeder seeder;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        seeder.seedIfEmpty(new EmployeeSeedGenerator(42, LocalDate.of(2026, 10, 1)).generate(200));
    }

    @Test
    void returnsFirstPageWithTotals() {
        PageResponse<EmployeeSummary> page = queries.search(EmployeeSearchCriteria.builder().size(25).build());

        assertThat(page.content()).hasSize(25);
        assertThat(page.totalElements()).isEqualTo(200);
        assertThat(page.totalPages()).isEqualTo(8);
        assertThat(page.page()).isZero();
    }

    @Test
    void lastPageHoldsTheRemainder() {
        PageResponse<EmployeeSummary> page = queries.search(EmployeeSearchCriteria.builder().page(2).size(75).build());

        assertThat(page.content()).hasSize(50);
    }

    @Test
    void filtersByOneOrMoreCountries() {
        var page = queries.search(EmployeeSearchCriteria.builder().countries(List.of("JP", "SG")).size(100).build());

        assertThat(page.totalElements()).isEqualTo(count("country_code in ('JP','SG')"));
        assertThat(page.content()).allSatisfy(e -> assertThat(e.countryCode()).isIn("JP", "SG"));
    }

    @Test
    void combinesFiltersWithAnd() {
        var criteria = EmployeeSearchCriteria.builder()
                .countries(List.of("IN")).levels(List.of(JobLevel.L3)).status(EmployeeStatus.ACTIVE).size(100).build();

        var page = queries.search(criteria);

        assertThat(page.totalElements()).isEqualTo(count("country_code = 'IN' and level = 'L3' and status = 'ACTIVE'"));
        assertThat(page.content()).allSatisfy(e -> {
            assertThat(e.countryCode()).isEqualTo("IN");
            assertThat(e.level()).isEqualTo(JobLevel.L3);
        });
    }

    @Test
    void searchesNameCaseInsensitivelyAndPartially() {
        String lastName = jdbc.queryForObject("select last_name from employee order by id limit 1", String.class);

        var page = queries.search(EmployeeSearchCriteria.builder()
                .search(lastName.substring(0, 3).toUpperCase()).size(100).build());

        assertThat(page.content()).isNotEmpty()
                .anySatisfy(e -> assertThat(e.fullName()).contains(lastName));
    }

    @Test
    void searchesByEmployeeCodeAndEmail() {
        assertThat(queries.search(EmployeeSearchCriteria.builder().search("E00007").build()).content())
                .singleElement().satisfies(e -> assertThat(e.employeeCode()).isEqualTo("E00007"));
        assertThat(queries.search(EmployeeSearchCriteria.builder().search(".7@acme").build()).content())
                .singleElement().satisfies(e -> assertThat(e.employeeCode()).isEqualTo("E00007"));
    }

    @Test
    void treatsLikeWildcardsLiterally() {
        assertThat(queries.search(EmployeeSearchCriteria.builder().search("%").build()).totalElements()).isZero();
        assertThat(queries.search(EmployeeSearchCriteria.builder().search("_").build()).totalElements()).isZero();
    }

    @Test
    void sortsBySalaryInUsdAcrossCurrencies() {
        var page = queries.search(EmployeeSearchCriteria.builder()
                .sort(EmployeeSortField.SALARY_USD, true).size(100).build());

        assertThat(page.content()).extracting(e -> e.salaryUsd().minorUnits())
                .isSortedAccordingTo((a, b) -> Long.compare(b, a));
    }

    @Test
    void returnsLocalAndUsdSalary() {
        var japanese = queries.search(EmployeeSearchCriteria.builder().countries(List.of("JP")).size(1).build())
                .content().getFirst();

        assertThat(japanese.salary().currency()).isEqualTo("JPY");
        assertThat(japanese.salaryUsd().currency()).isEqualTo("USD");
        assertThat(japanese.salaryUsd().minorUnits()).isPositive();
        assertThat(japanese.countryName()).isEqualTo("Japan");
        assertThat(japanese.department()).isNotBlank();
    }

    @Test
    void parsesSortParameterAgainstAWhitelist() {
        assertThat(EmployeeSortField.parse("salaryUsd,desc")).isEqualTo(new EmployeeSortField.Sort(EmployeeSortField.SALARY_USD, true));
        assertThat(EmployeeSortField.parse("name")).isEqualTo(new EmployeeSortField.Sort(EmployeeSortField.NAME, false));
        assertThatThrownBy(() -> EmployeeSortField.parse("1;drop table employee"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private long count(String where) {
        return jdbc.queryForObject("select count(*) from employee where " + where, Long.class);
    }
}
