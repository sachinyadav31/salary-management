package com.acme.salary.bulk.api;

import com.acme.salary.bonus.BonusType;
import com.acme.salary.bulk.BulkModels.Preview;
import com.acme.salary.bulk.BulkModels.Result;
import com.acme.salary.bulk.BulkPlanner.Kind;
import com.acme.salary.bulk.BulkPlanner.Request;
import com.acme.salary.bulk.BulkService;
import com.acme.salary.employee.api.EmployeeFilterParams;
import com.acme.salary.salary.SalaryChangeReason;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Bulk raises and bonuses for everyone matching the directory filters (the same query parameters as
 * {@code GET /api/employees}). Preview first; apply with a client-generated request id so a double submit or a retry
 * applies it once.
 */
@RestController
@RequestMapping("/api/bulk-changes")
@Tag(name = "Bulk changes", description = "Raise or bonus for a filtered group, previewed, then applied in one transaction")
public class BulkChangeController {

    /**
     * RAISE: {@code percent} and {@code reason}. BONUS: {@code bonusType} and either {@code percent} (of base salary) or
     * {@code amount} (in the selection's one currency). {@code requestId} is only needed to apply.
     */
    public record BulkChangeRequest(@NotNull Kind kind,
                                    @Positive @Digits(integer = 3, fraction = 2) BigDecimal percent,
                                    @Positive BigDecimal amount,
                                    @NotNull LocalDate effectiveDate,
                                    SalaryChangeReason reason,
                                    BonusType bonusType,
                                    @Size(max = 500) String note,
                                    UUID requestId) {

        Request toRequest() {
            return new Request(kind, percent, amount, effectiveDate, reason, bonusType, note);
        }
    }

    private final BulkService bulk;

    public BulkChangeController(BulkService bulk) {
        this.bulk = bulk;
    }

    @PostMapping("/preview")
    @Operation(summary = "What a bulk change would do, without saving anything")
    public Preview preview(@ParameterObject EmployeeFilterParams filter, @Valid @RequestBody BulkChangeRequest request) {
        return bulk.preview(filter.toCriteria().build(), request.toRequest());
    }

    @PostMapping
    @Operation(summary = "Apply a bulk change in one transaction (idempotent per requestId)")
    public Result apply(@ParameterObject EmployeeFilterParams filter, @Valid @RequestBody BulkChangeRequest request) {
        if (request.requestId() == null) {
            throw new IllegalArgumentException("requestId is required to apply a bulk change");
        }
        return bulk.apply(filter.toCriteria().build(), request.toRequest(), request.requestId());
    }
}
