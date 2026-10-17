package com.acme.salary.insights;

import com.acme.salary.common.Money;
import com.acme.salary.employee.VariableTarget;
import com.acme.salary.payband.BandPosition;
import com.acme.salary.reference.CurrencyConverter;
import com.acme.salary.reference.JobLevel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Loads the active workforce in one lean query (~10k rows, a few ms). */
@Component
public class WorkforceSnapshotLoader {

    private final JdbcTemplate jdbc;
    private final CurrencyConverter converter;
    private final Clock clock;

    public WorkforceSnapshotLoader(JdbcTemplate jdbc, CurrencyConverter converter, Clock clock) {
        this.jdbc = jdbc;
        this.converter = converter;
        this.clock = clock;
    }

    public WorkforceSnapshot load() {
        List<WorkforceMember> members = jdbc.query("""
                select e.country_code, c.name as country_name, e.current_salary_currency, e.department_id,
                       d.name as department_name, e.job_title, e.level, e.current_salary_minor,
                       e.current_salary_usd_minor, p.compa_ratio_bp, p.band_position, e.variable_target_bp,
                       (select coalesce(sum(b.amount_minor), 0) from bonus_award b
                        where b.employee_id = e.id and b.awarded_on > ? and b.awarded_on <= ?) as bonuses_12m
                from employee e
                join country c on c.code = e.country_code
                join department d on d.id = e.department_id
                left join employee_pay p on p.employee_id = e.id
                where e.status = 'ACTIVE'""",
                (rs, i) -> {
                    String position = rs.getString("band_position");
                    String currency = rs.getString("current_salary_currency");
                    Money salaryUsd = Money.ofMinor(rs.getLong("current_salary_usd_minor"), "USD");
                    return new WorkforceMember(
                            rs.getString("country_code"),
                            rs.getString("country_name"),
                            rs.getString("current_salary_currency"),
                            rs.getLong("department_id"),
                            rs.getString("department_name"),
                            rs.getString("job_title"),
                            JobLevel.valueOf(rs.getString("level")),
                            rs.getLong("current_salary_minor"),
                            rs.getLong("current_salary_usd_minor"),
                            position == null ? null : rs.getLong("compa_ratio_bp"),
                            position == null ? null : BandPosition.valueOf(position),
                            new VariableTarget(rs.getInt("variable_target_bp")).of(salaryUsd).minorUnits(),
                            converter.toUsd(Money.ofMinor(rs.getLong("bonuses_12m"), currency)).minorUnits());
                }, LocalDate.now(clock).minusYears(1).toString(), LocalDate.now(clock).toString());
        String fxAsOf = jdbc.queryForObject("select max(as_of_date) from fx_rate", String.class);
        return new WorkforceSnapshot(members, clock.instant(), fxAsOf);
    }
}
