package com.acme.salary.reference.api;

import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.reference.CurrencyConverter;
import com.acme.salary.reference.api.MetaResponse.CountryView;
import com.acme.salary.reference.api.MetaResponse.DepartmentView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MetaController {

    private final JdbcTemplate jdbc;

    public MetaController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/meta")
    public MetaResponse meta() {
        return new MetaResponse(
                jdbc.query("select code, name, currency_code from country order by name",
                        (rs, i) -> new CountryView(rs.getString(1), rs.getString(2), rs.getString(3))),
                jdbc.query("select id, name from department order by name",
                        (rs, i) -> new DepartmentView(rs.getLong(1), rs.getString(2))),
                jdbc.queryForList("select distinct job_title from employee order by job_title", String.class),
                List.of(JobLevel.values()),
                List.of(EmployeeStatus.values()),
                CurrencyConverter.REPORTING_CURRENCY,
                jdbc.queryForObject("select max(as_of_date) from fx_rate", String.class));
    }
}
