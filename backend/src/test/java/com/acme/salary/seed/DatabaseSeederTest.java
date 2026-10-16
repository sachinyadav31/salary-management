package com.acme.salary.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({DatabaseSeeder.class, DatabaseSeederTest.FixedClock.class})
class DatabaseSeederTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired DatabaseSeeder seeder;
    @Autowired JdbcTemplate jdbc;

    private final List<SeedEmployee> employees = new EmployeeSeedGenerator(42, LocalDate.of(2026, 10, 1)).generate(200);

    @Test
    void insertsEmployeesWithTheirSalaryHistory() {
        int inserted = seeder.seedIfEmpty(employees);

        int expectedRecords = employees.stream().mapToInt(e -> e.salaryHistory().size()).sum();
        assertThat(inserted).isEqualTo(200);
        assertThat(count("employee")).isEqualTo(200);
        assertThat(count("salary_record")).isEqualTo(expectedRecords);
    }

    @Test
    void currentSalaryMatchesLatestHistoryRecordInTheDatabase() {
        seeder.seedIfEmpty(employees);

        Integer mismatches = jdbc.queryForObject("""
                select count(*) from employee e
                where e.current_salary_minor <> (
                    select s.amount_minor from salary_record s where s.employee_id = e.id
                    order by s.effective_date desc, s.id desc limit 1)""", Integer.class);
        assertThat(mismatches).isZero();
    }

    @Test
    void isIdempotent() {
        seeder.seedIfEmpty(employees);
        int secondRun = seeder.seedIfEmpty(employees);

        assertThat(secondRun).isZero();
        assertThat(count("employee")).isEqualTo(200);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
