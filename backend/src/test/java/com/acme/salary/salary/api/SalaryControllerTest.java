package com.acme.salary.salary.api;

import com.acme.salary.common.ConflictException;
import com.acme.salary.common.Money;
import com.acme.salary.employee.query.EmployeeDetail;
import com.acme.salary.employee.query.EmployeeQueryRepository;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.salary.SalaryHistory.Entry;
import com.acme.salary.salary.SalaryHistory.EntryStatus;
import com.acme.salary.salary.SalaryHistory.TimelineEntry;
import com.acme.salary.salary.SalaryService;
import com.acme.salary.salary.SalaryService.RecordChange;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebMvcTest(SalaryController.class)
class SalaryControllerTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean SalaryService salaryService;
    @MockitoBean EmployeeQueryRepository employeeQueries;

    private static final List<TimelineEntry> TIMELINE = List.of(
            new TimelineEntry(new Entry(9, Money.ofMinor(8_800_000, "GBP"), LocalDate.of(2026, 10, 16),
                    SalaryChangeReason.PROMOTION, "Led the payments migration"), EntryStatus.CURRENT, new BigDecimal("10.0")),
            new TimelineEntry(new Entry(4, Money.ofMinor(8_000_000, "GBP"), LocalDate.of(2024, 1, 15),
                    SalaryChangeReason.HIRE, null), EntryStatus.PAST, null));

    @Test
    void returnsTimelineNewestFirst() {
        when(salaryService.timeline(7)).thenReturn(TIMELINE);

        var json = assertThat(mvc.get().uri("/api/employees/7/salary-history")).hasStatusOk().bodyJson();
        json.extractingPath("$[0].status").isEqualTo("CURRENT");
        json.extractingPath("$[0].amount.amount").isEqualTo("88000.00");
        json.extractingPath("$[0].changePercent").isEqualTo(10.0);
        json.extractingPath("$[1].reason").isEqualTo("HIRE");
        json.extractingPath("$[1].changePercent").isNull();
    }

    @Test
    void recordsChangeAndReturns201WithEmployeeAndHistory() {
        when(salaryService.timeline(7)).thenReturn(TIMELINE);
        when(employeeQueries.findDetail(7)).thenReturn(Optional.of(new EmployeeDetail(null, 1, null, null)));

        assertThat(mvc.post().uri("/api/employees/7/salary-changes").contentType(MediaType.APPLICATION_JSON).content("""
                {"amount": "88000", "effectiveDate": "2026-10-16", "reason": "PROMOTION",
                 "note": "Led the payments migration", "version": 0}"""))
                .hasStatus(201)
                .hasHeader("Location", "/api/employees/7/salary-history")
                .bodyJson().extractingPath("$.history.length()").isEqualTo(2);

        ArgumentCaptor<RecordChange> captor = ArgumentCaptor.forClass(RecordChange.class);
        verify(salaryService).recordChange(eq(7L), captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("88000");
        assertThat(captor.getValue().reason()).isEqualTo(SalaryChangeReason.PROMOTION);
        assertThat(captor.getValue().expectedVersion()).isZero();
    }

    @Test
    void validatesRequestShape() {
        var json = assertThat(mvc.post().uri("/api/employees/7/salary-changes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": \"-1\", \"reason\": \"PROMOTION\"}")).hasStatus(400).bodyJson();
        json.extractingPath("$.errors").asMap().containsOnlyKeys("amount", "effectiveDate", "version");
        verifyNoInteractions(salaryService);
    }

    @Test
    void domainRuleViolationIs400AndStaleVersionIs409() {
        when(salaryService.recordChange(eq(7L), any()))
                .thenThrow(new IllegalArgumentException("Effective date cannot be before the hire date 2024-01-15"))
                .thenThrow(new ConflictException("Employee 7 was changed by someone else. Reload and try again."));
        String body = """
                {"amount": "88000", "effectiveDate": "2020-01-01", "reason": "PROMOTION", "version": 0}""";

        assertThat(mvc.post().uri("/api/employees/7/salary-changes").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(400).bodyJson().extractingPath("$.detail").asString().contains("hire date");
        assertThat(mvc.post().uri("/api/employees/7/salary-changes").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(409);
    }
}
