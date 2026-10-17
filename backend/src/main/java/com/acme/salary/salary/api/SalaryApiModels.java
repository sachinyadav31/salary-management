package com.acme.salary.salary.api;

import com.acme.salary.common.web.MoneyView;
import com.acme.salary.employee.query.EmployeeDetail;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.salary.SalaryHistory.EntryStatus;
import com.acme.salary.salary.SalaryHistory.TimelineEntry;
import com.acme.salary.salary.SalaryService.RecordChange;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class SalaryApiModels {

    private SalaryApiModels() {
    }

    /** Amount only: the currency is always the employee's country currency. */
    public record RecordSalaryChangeRequest(
            @NotNull @Positive BigDecimal amount,
            @NotNull LocalDate effectiveDate,
            @NotNull SalaryChangeReason reason,
            @Size(max = 500) String note,
            @NotNull Long version) {

        RecordChange toCommand() {
            return new RecordChange(amount, effectiveDate, reason, note, version);
        }
    }

    /** {@code recordedBy} is who recorded the change; null for system-generated history. */
    public record SalaryHistoryItem(long id, MoneyView amount, LocalDate effectiveDate, SalaryChangeReason reason,
                                    String note, EntryStatus status, BigDecimal changePercent, String recordedBy) {

        static SalaryHistoryItem from(TimelineEntry t) {
            return new SalaryHistoryItem(t.entry().sequence(), MoneyView.from(t.entry().amount()),
                    t.entry().effectiveDate(), t.entry().reason(), t.entry().note(), t.status(), t.changePercent(),
                    t.entry().recordedBy());
        }
    }

    /** Everything the UI needs to refresh after a change: the employee (new version) and the timeline. */
    public record SalaryChangeResponse(EmployeeDetail employee, List<SalaryHistoryItem> history) {
    }
}
