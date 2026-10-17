package com.acme.salary.payband;

import com.acme.salary.common.ClockConfig;
import com.acme.salary.reference.JobLevel;
import com.acme.salary.reference.ReferenceConfig;
import com.acme.salary.seed.DatabaseSeeder;
import com.acme.salary.seed.EmployeeSeedGenerator;
import com.acme.salary.seed.SeedCatalogAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({DatabaseSeeder.class, PayBandQueryRepository.class, ReferenceConfig.class, ClockConfig.class})
class PayBandDataTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired DatabaseSeeder seeder;
    @Autowired PayBandQueryRepository payBands;

    @Test
    void everyCountryAndLevelHasAWellFormedBandInTheCountryCurrency() {
        assertThat(jdbc.queryForObject("select count(*) from pay_band", Integer.class)).isEqualTo(8 * 6);
        assertThat(jdbc.queryForObject("""
                select count(*) from pay_band b join country c on c.code = b.country_code
                where b.currency_code <> c.currency_code or not (b.min_minor < b.mid_minor and b.mid_minor < b.max_minor)
                """, Integer.class)).isZero();
    }

    @Test
    void bandMidpointsMatchTheModelUsedToGenerateSeedData() {
        for (Map<String, Object> band : jdbc.queryForList("select country_code, level, mid_minor from pay_band")) {
            String country = (String) band.get("country_code");
            JobLevel level = JobLevel.valueOf((String) band.get("level"));
            assertThat(((Number) band.get("mid_minor")).longValue())
                    .as("%s %s", country, level)
                    .isEqualTo(SeedCatalogAccess.bandMidpointMinor(country, level));
        }
    }

    @Test
    void viewComputesCompaRatioAndBandPosition() {
        // US L3 band: mid 119,000.00 USD, min 95,200.00, max 142,800.00
        long atMid = insertUsL3(11_900_000);
        long below = insertUsL3(9_519_900);
        long above = insertUsL3(14_280_100);
        long rounding = insertUsL3(11_542_405); // 0.969950... -> 9700 bp (half-up)

        assertThat(compa(atMid)).isEqualTo(Map.of("bp", 10_000L, "position", "WITHIN"));
        assertThat(compa(below)).isEqualTo(Map.of("bp", 8_000L, "position", "BELOW"));
        assertThat(compa(above)).isEqualTo(Map.of("bp", 12_000L, "position", "ABOVE"));
        assertThat(compa(rounding).get("bp")).isEqualTo(9_700L);
    }

    @Test
    void seededWorkforceHasRealisticOutliers() {
        seeder.seedIfEmpty(new EmployeeSeedGenerator(42, LocalDate.of(2026, 10, 1)).generate(2_000));

        Map<String, Long> byPosition = new java.util.HashMap<>();
        jdbc.query("select band_position, count(*) from employee_pay group by band_position",
                rs -> { byPosition.put(rs.getString(1), rs.getLong(2)); });
        long total = byPosition.values().stream().mapToLong(Long::longValue).sum();

        assertThat(total).isEqualTo(2_000);
        assertThat(byPosition.get("WITHIN")).isGreaterThan(total * 70 / 100);
        assertThat(byPosition.get("BELOW")).isPositive();
        assertThat(byPosition.get("ABOVE")).isPositive();
    }

    private long insertUsL3(long salaryMinor) {
        long id = jdbc.queryForObject("select coalesce(max(id), 0) + 1 from employee", Long.class);
        jdbc.update("""
                insert into employee (id, employee_code, first_name, last_name, email, country_code, department_id,
                    job_title, level, hire_date, status, current_salary_minor, current_salary_currency,
                    current_salary_usd_minor, created_at, updated_at)
                values (?, ?, 'T', 'T', ?, 'US', 1, 'Engineer', 'L3', '2024-01-01', 'ACTIVE', ?, 'USD', ?,
                    '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')""",
                id, "T" + id, "t" + id + "@acme.test", salaryMinor, salaryMinor);
        return id;
    }

    private Map<String, Object> compa(long employeeId) {
        return jdbc.queryForObject("select compa_ratio_bp, band_position from employee_pay where employee_id = ?",
                (rs, i) -> Map.of("bp", rs.getLong(1), "position", rs.getString(2)), employeeId);
    }

    @Test
    void bandDistributionAccountsForEveryActiveEmployee() {
        seeder.seedIfEmpty(new EmployeeSeedGenerator(42, LocalDate.of(2026, 10, 1)).generate(500));

        var bands = payBands.findAllWithDistribution();

        assertThat(bands).hasSize(48);
        assertThat(bands).allSatisfy(b -> assertThat(b.below() + b.within() + b.above()).isEqualTo(b.headcount()));
        assertThat(bands.stream().mapToLong(PayBandSummary::headcount).sum())
                .isEqualTo(jdbc.queryForObject("select count(*) from employee where status = 'ACTIVE'", Long.class));
        assertThat(bands.getFirst().countryName()).isEqualTo("Australia");
        assertThat(bands.getFirst().level()).isEqualTo(JobLevel.L1);
    }
}
