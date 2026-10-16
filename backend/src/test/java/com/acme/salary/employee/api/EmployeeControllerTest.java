package com.acme.salary.employee.api;

import com.acme.salary.common.Money;
import com.acme.salary.common.web.MoneyView;
import com.acme.salary.common.web.PageResponse;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.employee.query.EmployeeQueryRepository;
import com.acme.salary.employee.query.EmployeeSearchCriteria;
import com.acme.salary.employee.query.EmployeeSortField;
import com.acme.salary.employee.query.EmployeeSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(EmployeeController.class)
class EmployeeControllerTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean EmployeeQueryRepository queries;

    private static final EmployeeSummary AKIRA = new EmployeeSummary(7, "E00007", "Akira", "Ito", "Akira Ito",
            "akira.ito.7@acme.example", "JP", "Japan", 1, "Engineering", "Software Engineer", JobLevel.L2,
            EmployeeStatus.ACTIVE, LocalDate.of(2021, 4, 1),
            MoneyView.from(Money.ofMinor(9_000_000, "JPY")), MoneyView.from(Money.ofMinor(6_030_000, "USD")));

    @Test
    void bindsQueryParametersIntoSearchCriteria() {
        when(queries.search(any())).thenReturn(PageResponse.of(List.of(), 1, 50, 0));

        assertThat(mvc.get().uri("/api/employees?search=ito&country=JP&country=IN&department=1&title=Software Engineer"
                + "&level=L2&level=L3&status=ACTIVE&page=1&size=50&sort=salaryUsd,desc")).hasStatusOk();

        ArgumentCaptor<EmployeeSearchCriteria> captor = ArgumentCaptor.forClass(EmployeeSearchCriteria.class);
        verify(queries).search(captor.capture());
        EmployeeSearchCriteria c = captor.getValue();
        assertThat(c.search()).isEqualTo("ito");
        assertThat(c.countries()).containsExactly("JP", "IN");
        assertThat(c.departmentIds()).containsExactly(1L);
        assertThat(c.jobTitle()).isEqualTo("Software Engineer");
        assertThat(c.levels()).containsExactly(JobLevel.L2, JobLevel.L3);
        assertThat(c.status()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(c.page()).isEqualTo(1);
        assertThat(c.size()).isEqualTo(50);
        assertThat(c.sort()).isEqualTo(new EmployeeSortField.Sort(EmployeeSortField.SALARY_USD, true));
    }

    @Test
    void returnsPageWithMoneyAsExactMinorUnitsAndDecimalString() {
        when(queries.search(any())).thenReturn(PageResponse.of(List.of(AKIRA), 0, 25, 1));

        var json = assertThat(mvc.get().uri("/api/employees")).hasStatusOk().bodyJson();
        json.extractingPath("$.totalElements").isEqualTo(1);
        json.extractingPath("$.content[0].fullName").isEqualTo("Akira Ito");
        json.extractingPath("$.content[0].hireDate").isEqualTo("2021-04-01");
        json.extractingPath("$.content[0].salary.minorUnits").isEqualTo(9_000_000);
        json.extractingPath("$.content[0].salary.amount").isEqualTo("9000000");
        json.extractingPath("$.content[0].salaryUsd.amount").isEqualTo("60300.00");
    }

    @Test
    void rejectsOversizedPagesAsProblemDetails() {
        assertThat(mvc.get().uri("/api/employees?size=500"))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").asString().contains("size");
    }

    @Test
    void rejectsUnknownSortField() {
        assertThat(mvc.get().uri("/api/employees?sort=password")).hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void rejectsUnknownLevel() {
        assertThat(mvc.get().uri("/api/employees?level=L9")).hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
