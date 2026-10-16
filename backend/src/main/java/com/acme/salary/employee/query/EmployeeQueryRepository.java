package com.acme.salary.employee.query;

import com.acme.salary.common.Money;
import com.acme.salary.common.web.MoneyView;
import com.acme.salary.common.web.PageResponse;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.JobLevel;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read side of the employee directory. Plain SQL with bound parameters: selects only the columns the
 * list needs and joins reference data in one query (no N+1, no entity hydration).
 */
@Repository
public class EmployeeQueryRepository {

    private static final String FROM = """
            from employee e
            join country c on c.code = e.country_code
            join department d on d.id = e.department_id
            """;

    private static final String COLUMNS = """
            select e.id, e.employee_code, e.first_name, e.last_name, e.email, e.country_code,
                   c.name as country_name, e.department_id, d.name as department_name, e.job_title, e.level,
                   e.status, e.hire_date, e.current_salary_minor, e.current_salary_currency,
                   e.current_salary_usd_minor, e.version, e.created_at, e.updated_at
            """;

    private static final RowMapper<EmployeeSummary> ROW_MAPPER = (rs, i) -> new EmployeeSummary(
            rs.getLong("id"),
            rs.getString("employee_code"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getString("first_name") + " " + rs.getString("last_name"),
            rs.getString("email"),
            rs.getString("country_code"),
            rs.getString("country_name"),
            rs.getLong("department_id"),
            rs.getString("department_name"),
            rs.getString("job_title"),
            JobLevel.valueOf(rs.getString("level")),
            EmployeeStatus.valueOf(rs.getString("status")),
            LocalDate.parse(rs.getString("hire_date")),
            MoneyView.from(Money.ofMinor(rs.getLong("current_salary_minor"), rs.getString("current_salary_currency"))),
            MoneyView.from(Money.ofMinor(rs.getLong("current_salary_usd_minor"), "USD")));

    private final NamedParameterJdbcTemplate jdbc;

    public EmployeeQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<EmployeeSummary> search(EmployeeSearchCriteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = whereClause(criteria, params);

        Long total = jdbc.queryForObject("select count(*) " + FROM + where, params, Long.class);

        params.addValue("limit", criteria.size());
        params.addValue("offset", (long) criteria.page() * criteria.size());
        List<EmployeeSummary> rows = jdbc.query(COLUMNS + FROM + where
                + " order by " + criteria.sort().orderBy() + ", e.id asc limit :limit offset :offset",
                params, ROW_MAPPER);

        return PageResponse.of(rows, criteria.page(), criteria.size(), total == null ? 0 : total);
    }

    public Optional<EmployeeDetail> findDetail(long id) {
        return jdbc.query(COLUMNS + FROM + " where e.id = :id", new MapSqlParameterSource("id", id),
                (rs, i) -> new EmployeeDetail(ROW_MAPPER.mapRow(rs, i), rs.getLong("version"),
                        Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("updated_at"))))
                .stream().findFirst();
    }

    private static String whereClause(EmployeeSearchCriteria c, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();
        if (c.search() != null && !c.search().isBlank()) {
            params.addValue("search", "%" + escapeLike(c.search().trim()) + "%");
            // SQLite LIKE is case-insensitive for ASCII.
            conditions.add("""
                    (e.first_name || ' ' || e.last_name like :search escape '\\'
                     or e.email like :search escape '\\'
                     or e.employee_code like :search escape '\\')""");
        }
        if (!c.countries().isEmpty()) {
            params.addValue("countries", c.countries());
            conditions.add("e.country_code in (:countries)");
        }
        if (!c.departmentIds().isEmpty()) {
            params.addValue("departmentIds", c.departmentIds());
            conditions.add("e.department_id in (:departmentIds)");
        }
        if (c.jobTitle() != null && !c.jobTitle().isBlank()) {
            params.addValue("jobTitle", c.jobTitle());
            conditions.add("e.job_title = :jobTitle");
        }
        if (!c.levels().isEmpty()) {
            params.addValue("levels", c.levels().stream().map(Enum::name).toList());
            conditions.add("e.level in (:levels)");
        }
        if (c.status() != null) {
            params.addValue("status", c.status().name());
            conditions.add("e.status = :status");
        }
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
