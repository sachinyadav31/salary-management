package com.acme.salary.employee.api;

import com.acme.salary.common.ConflictException;
import com.acme.salary.common.Money;
import com.acme.salary.common.web.MoneyView;
import com.acme.salary.common.web.PageResponse;
import com.acme.salary.employee.EmployeeCommands.CreateEmployee;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.query.EmployeeDetail;
import com.acme.salary.employee.query.EmployeeQueryRepository;
import com.acme.salary.employee.query.EmployeeSearchCriteria;
import com.acme.salary.employee.query.EmployeeSortField;
import com.acme.salary.employee.query.EmployeeSummary;
import com.acme.salary.payband.BandPosition;
import com.acme.salary.payband.PayBandView;
import com.acme.salary.reference.JobLevel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(EmployeeController.class)
@Import(EmployeeControllerTest.FixedClock.class)
class EmployeeControllerTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-17T09:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired MockMvcTester mvc;
    @MockitoBean EmployeeQueryRepository queries;
    @MockitoBean EmployeeService service;

    private static final EmployeeSummary AKIRA = new EmployeeSummary(7, "E00007", "Akira", "Ito", "Akira Ito",
            "akira.ito.7@acme.example", "JP", "Japan", 1, "Engineering", "Software Engineer", JobLevel.L2,
            EmployeeStatus.ACTIVE, LocalDate.of(2021, 4, 1),
            MoneyView.from(Money.ofMinor(9_000_000, "JPY")), MoneyView.from(Money.ofMinor(6_030_000, "USD")),
            new PayBandView(MoneyView.from(Money.ofMinor(4_680_000, "JPY")), MoneyView.from(Money.ofMinor(5_850_000, "JPY")),
                    MoneyView.from(Money.ofMinor(7_020_000, "JPY"))),
            new BigDecimal("1.54"), BandPosition.ABOVE);

    @Test
    void bindsQueryParametersIntoSearchCriteria() {
        when(queries.search(any())).thenReturn(PageResponse.of(List.of(), 1, 50, 0));

        assertThat(mvc.get().uri("/api/employees?search=ito&country=JP&country=IN&department=1&title=Software Engineer"
                + "&level=L2&level=L3&status=ACTIVE&bandPosition=BELOW&compaMin=0.5&compaMax=0.85"
                + "&page=1&size=50&sort=salaryUsd,desc")).hasStatusOk();

        ArgumentCaptor<EmployeeSearchCriteria> captor = ArgumentCaptor.forClass(EmployeeSearchCriteria.class);
        verify(queries).search(captor.capture());
        EmployeeSearchCriteria c = captor.getValue();
        assertThat(c.search()).isEqualTo("ito");
        assertThat(c.countries()).containsExactly("JP", "IN");
        assertThat(c.departmentIds()).containsExactly(1L);
        assertThat(c.jobTitle()).isEqualTo("Software Engineer");
        assertThat(c.levels()).containsExactly(JobLevel.L2, JobLevel.L3);
        assertThat(c.status()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(c.bandPosition()).isEqualTo(BandPosition.BELOW);
        assertThat(c.compaMin()).isEqualByComparingTo("0.5");
        assertThat(c.compaMax()).isEqualByComparingTo("0.85");
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
        json.extractingPath("$.content[0].payBand.mid.amount").isEqualTo("5850000");
        json.extractingPath("$.content[0].compaRatio").isEqualTo(1.54);
        json.extractingPath("$.content[0].bandPosition").isEqualTo("ABOVE");
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
    void rejectsInvertedCompaRange() {
        assertThat(mvc.get().uri("/api/employees?compaMin=1.2&compaMax=0.8")).hasStatus(400)
                .bodyJson().extractingPath("$.detail").asString().contains("compaMin");
    }

    @Test
    void rejectsUnknownLevel() {
        assertThat(mvc.get().uri("/api/employees?level=L9")).hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private static final EmployeeDetail AKIRA_DETAIL = new EmployeeDetail(AKIRA, 3,
            Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-16T10:00:00Z"));

    private static final String VALID_CREATE = """
            {"firstName": "Akira", "lastName": "Ito", "email": "akira@acme.example", "countryCode": "JP",
             "departmentId": 1, "jobTitle": "Software Engineer", "level": "L2", "hireDate": "2026-11-01",
             "salaryAmount": "9000000"}""";

    @Test
    void getsDetailAsFlatJsonWithVersion() {
        when(queries.findDetail(7)).thenReturn(Optional.of(AKIRA_DETAIL));

        var json = assertThat(mvc.get().uri("/api/employees/7")).hasStatusOk().bodyJson();
        json.extractingPath("$.employeeCode").isEqualTo("E00007");
        json.extractingPath("$.version").isEqualTo(3);
        json.extractingPath("$.updatedAt").isEqualTo("2026-10-16T10:00:00Z");
    }

    @Test
    void unknownEmployeeIs404() {
        when(queries.findDetail(anyLong())).thenReturn(Optional.empty());

        assertThat(mvc.get().uri("/api/employees/999")).hasStatus(404)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void createReturns201WithLocation() {
        when(service.create(any())).thenReturn(7L);
        when(queries.findDetail(7)).thenReturn(Optional.of(AKIRA_DETAIL));

        assertThat(mvc.post().uri("/api/employees").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .hasStatus(201)
                .hasHeader("Location", "/api/employees/7");

        ArgumentCaptor<CreateEmployee> captor = ArgumentCaptor.forClass(CreateEmployee.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().salaryAmount()).isEqualByComparingTo(new BigDecimal("9000000"));
        assertThat(captor.getValue().hireDate()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void createReportsEveryInvalidField() {
        String invalid = """
                {"firstName": " ", "lastName": "Ito", "email": "not-an-email", "countryCode": "japan",
                 "departmentId": 1, "jobTitle": "Engineer", "level": "L2", "hireDate": "2026-11-01",
                 "salaryAmount": "-5"}""";

        var json = assertThat(mvc.post().uri("/api/employees").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .hasStatus(400).bodyJson();
        json.extractingPath("$.title").isEqualTo("Validation failed");
        json.extractingPath("$.errors").asMap().containsOnlyKeys("firstName", "email", "countryCode", "salaryAmount");
        verifyNoInteractions(service);
    }

    @Test
    void staleUpdateIs409() {
        doThrow(new ConflictException("Employee 7 was changed by someone else. Reload and try again."))
                .when(service).updateProfile(eq(7L), any());
        String update = """
                {"firstName": "Akira", "lastName": "Ito", "email": "akira@acme.example", "departmentId": 1,
                 "jobTitle": "Staff Engineer", "level": "L4", "version": 2}""";

        assertThat(mvc.put().uri("/api/employees/7").contentType(MediaType.APPLICATION_JSON).content(update))
                .hasStatus(409)
                .bodyJson().extractingPath("$.detail").asString().contains("Reload");
    }

    @Test
    void changesStatusWithExpectedVersion() {
        when(queries.findDetail(7)).thenReturn(Optional.of(AKIRA_DETAIL));

        assertThat(mvc.patch().uri("/api/employees/7/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"INACTIVE\", \"version\": 3}")).hasStatusOk();

        verify(service).changeStatus(7L, EmployeeStatus.INACTIVE, 3L);
    }

    @Test
    void statusChangeRequiresVersion() {
        assertThat(mvc.patch().uri("/api/employees/7/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"INACTIVE\"}")).hasStatus(400);
        verifyNoInteractions(service);
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportsFilteredEmployeesAsExcelFriendlyCsv() throws Exception {
        EmployeeSummary hostile = new EmployeeSummary(8, "E00008", "=HYPERLINK(\"http://x\")", "Müller, Jr", "Mueller",
                "m@acme.example", "DE", "Germany", 4, "Sales", "Account Executive", JobLevel.L2, EmployeeStatus.ACTIVE,
                LocalDate.of(2022, 3, 1), MoneyView.from(Money.ofMinor(6_000_000, "EUR")),
                MoneyView.from(Money.ofMinor(6_480_000, "USD")), null, null, null);
        doAnswer(inv -> {
            Consumer<EmployeeSummary> sink = inv.getArgument(1);
            sink.accept(AKIRA);
            sink.accept(hostile);
            return null;
        }).when(queries).stream(any(), any(Consumer.class));

        var result = mvc.get().uri("/api/employees/export.csv?country=JP&country=DE&bandPosition=ABOVE&sort=compaRatio,desc")
                .exchange();

        assertThat(result).hasStatusOk()
                .hasHeader("Content-Disposition", "attachment; filename=\"employees-2026-10-17.csv\"");
        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).startsWith("\uFEFFEmployee code,First name,Last name,");
        String[] lines = body.split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[1]).isEqualTo("E00007,Akira,Ito,akira.ito.7@acme.example,Japan,Engineering,Software Engineer,"
                + "L2,ACTIVE,2021-04-01,9000000,JPY,60300.00,4680000,5850000,7020000,1.54,ABOVE");
        assertThat(lines[2]).startsWith("E00008,\"'=HYPERLINK(\"\"http://x\"\")\",\"Müller, Jr\",")
                .endsWith(",60000.00,EUR,64800.00,,,,,");

        ArgumentCaptor<EmployeeSearchCriteria> captor = ArgumentCaptor.forClass(EmployeeSearchCriteria.class);
        verify(queries).stream(captor.capture(), any(Consumer.class));
        assertThat(captor.getValue().countries()).containsExactly("JP", "DE");
        assertThat(captor.getValue().bandPosition()).isEqualTo(BandPosition.ABOVE);
        assertThat(captor.getValue().sort()).isEqualTo(new EmployeeSortField.Sort(EmployeeSortField.COMPA_RATIO, true));
    }
}
