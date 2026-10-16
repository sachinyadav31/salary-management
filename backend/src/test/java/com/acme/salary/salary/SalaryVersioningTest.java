package com.acme.salary.salary;

import com.acme.salary.employee.EmployeeCommands.CreateEmployee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.salary.SalaryService.RecordChange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Version bumps happen at commit, so this test runs without a wrapping test transaction:
 * every service call commits for real, and the data is removed afterwards.
 */
@SpringBootTest
@ActiveProfiles("test")
class SalaryVersioningTest {

    @Autowired EmployeeService employeeService;
    @Autowired SalaryService salaryService;
    @Autowired EmployeeRepository employees;
    @Autowired JdbcTemplate jdbc;

    long id;

    @BeforeEach
    void hire() {
        id = employeeService.create(new CreateEmployee("Grace", "Hopper", "grace.versioning@acme.test", "US", 1,
                "Software Engineer", JobLevel.L4, LocalDate.of(2024, 1, 15), new BigDecimal("150000")));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from salary_record where employee_id = ?", id);
        jdbc.update("delete from employee where id = ?", id);
    }

    @Test
    void everySalaryChangeBumpsTheVersionExactlyOnce() {
        LocalDate today = LocalDate.now();

        salaryService.recordChange(id, new RecordChange(new BigDecimal("160000"), today, SalaryChangeReason.PROMOTION, null, 0));
        assertThat(version()).as("change in effect now").isEqualTo(1);

        salaryService.recordChange(id, new RecordChange(new BigDecimal("170000"), today.plusMonths(3),
                SalaryChangeReason.ANNUAL_REVIEW, null, 1));
        assertThat(version()).as("scheduled change").isEqualTo(2);
    }

    private long version() {
        return employees.findById(id).orElseThrow().getVersion();
    }
}
