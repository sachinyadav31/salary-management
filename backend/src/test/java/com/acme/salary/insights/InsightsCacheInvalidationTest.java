package com.acme.salary.insights;

import com.acme.salary.employee.EmployeeCommands.CreateEmployee;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.employee.JobLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs real, committing transactions to prove the cache reacts to commits and ignores rollbacks. */
@SpringBootTest
@ActiveProfiles("test")
class InsightsCacheInvalidationTest {

    @Autowired WorkforceSnapshotCache cache;
    @Autowired EmployeeService employeeService;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from salary_record where employee_id in (select id from employee where email like '%@cache.test')");
        jdbc.update("delete from employee where email like '%@cache.test'");
        cache.invalidate();
    }

    @Test
    void committedWriteIsVisibleOnTheNextRead() {
        int before = cache.get().members().size();

        employeeService.create(hire("committed@cache.test"));

        assertThat(cache.get().members()).hasSize(before + 1);
    }

    @Test
    void rolledBackWriteDoesNotEvictTheCache() {
        WorkforceSnapshot before = cache.get();

        tx.executeWithoutResult(status -> {
            employeeService.create(hire("rolledback@cache.test"));
            status.setRollbackOnly();
        });

        assertThat(cache.get()).isSameAs(before);
    }

    private static CreateEmployee hire(String email) {
        return new CreateEmployee("Cache", "Test", email, "US", 1, "Software Engineer", JobLevel.L2,
                LocalDate.of(2026, 1, 1), new BigDecimal("100000"));
    }
}
