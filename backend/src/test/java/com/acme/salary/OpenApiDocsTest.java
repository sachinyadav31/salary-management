package com.acme.salary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {

    @Autowired MockMvcTester mvc;

    @Test
    void publishesTheApiSpecWithEveryFeature() {
        var json = assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk().bodyJson();
        json.extractingPath("$.info.title").isEqualTo("ACME Salary Management API");
        json.extractingPath("$.paths").asMap().containsKeys(
                "/api/employees", "/api/employees/export.csv", "/api/employees/{id}",
                "/api/employees/{employeeId}/salary-changes", "/api/insights/summary", "/api/insights/outliers",
                "/api/pay-bands", "/api/meta");
    }

    @Test
    void directoryFiltersAppearAsIndividualQueryParameters() {
        assertThat(mvc.get().uri("/v3/api-docs")).bodyJson()
                .extractingPath("$.paths['/api/employees'].get.parameters[*].name").asArray()
                .contains("search", "country", "level", "bandPosition", "compaMax", "sort", "page", "size");
    }

    @Test
    void servesSwaggerUi() {
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatusOk();
    }
}
