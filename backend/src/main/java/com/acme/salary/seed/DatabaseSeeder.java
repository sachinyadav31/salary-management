package com.acme.salary.seed;

import com.acme.salary.reference.CurrencyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes generated employees with plain JDBC batch inserts (10k rows in about a second on SQLite).
 * Idempotent: does nothing if any employee already exists.
 */
@Component
public class DatabaseSeeder {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CurrencyConverter converter;

    public DatabaseSeeder(JdbcTemplate jdbc, Clock clock, CurrencyConverter converter) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Transactional
    public int seedIfEmpty(List<SeedEmployee> employees) {
        Integer existing = jdbc.queryForObject("select count(*) from employee", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Seed skipped: {} employees already present", existing);
            return 0;
        }
        long started = System.nanoTime();
        Map<String, Long> departmentIds = departmentIdsByName();
        String now = clock.instant().toString();

        List<Object[]> employeeRows = new ArrayList<>(employees.size());
        List<Object[]> salaryRows = new ArrayList<>(employees.size() * 2);
        long id = 1;
        for (SeedEmployee e : employees) {
            employeeRows.add(new Object[]{id, e.employeeCode(), e.firstName(), e.lastName(), e.email(),
                    e.countryCode(), departmentIds.get(e.departmentName()), e.jobTitle(), e.level().name(),
                    e.hireDate().toString(), e.status().name(), e.currentSalary().minorUnits(),
                    e.currentSalary().currencyCode(), converter.toUsd(e.currentSalary()).minorUnits(), now, now});
            for (SeedSalaryRecord r : e.salaryHistory()) {
                salaryRows.add(new Object[]{id, r.amount().minorUnits(), r.amount().currencyCode(),
                        r.effectiveDate().toString(), r.reason().name(), now});
            }
            id++;
        }

        jdbc.batchUpdate("""
                insert into employee (id, employee_code, first_name, last_name, email, country_code, department_id,
                    job_title, level, hire_date, status, current_salary_minor, current_salary_currency,
                    current_salary_usd_minor, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", employeeRows);
        jdbc.batchUpdate("""
                insert into salary_record (employee_id, amount_minor, currency_code, effective_date, reason, created_at)
                values (?, ?, ?, ?, ?, ?)""", salaryRows);

        log.info("Seeded {} employees and {} salary records in {} ms", employeeRows.size(), salaryRows.size(),
                (System.nanoTime() - started) / 1_000_000);
        return employeeRows.size();
    }

    private Map<String, Long> departmentIdsByName() {
        Map<String, Long> ids = new HashMap<>();
        jdbc.query("select id, name from department", rs -> {
            ids.put(rs.getString("name"), rs.getLong("id"));
        });
        return ids;
    }
}
