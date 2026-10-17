package com.acme.salary.common.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI salaryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ACME Salary Management API")
                .version("v1")
                .description("""
                        Manage salaries for ~10,000 employees across countries and answer "how do we pay people?".

                        Conventions: money is exact (`minorUnits` + ISO `currency` + decimal `amount` string); \
                        org-wide figures are in USD using fixed, dated FX rates; errors are RFC 7807 problem \
                        details; writes that modify an employee require the `version` you last read \
                        (optimistic locking, 409 on conflict)."""));
    }
}
