package com.acme.salary.bulk;

import com.acme.salary.bulk.BulkPlanner.Kind;
import com.acme.salary.common.web.MoneyView;
import com.acme.salary.payband.BandPosition;

import java.util.List;
import java.util.UUID;

/** What the bulk API returns. */
public final class BulkModels {

    private BulkModels() {
    }

    /** One planned change. For a raise {@code to} is the new salary; for a bonus it's null and {@code amount} is set. */
    public record Row(long employeeId, String employeeCode, String name, MoneyView from, MoneyView to, MoneyView amount,
                      BandPosition bandBefore, BandPosition bandAfter) {
    }

    /** Everyone skipped for the same reason, with a few names as examples. */
    public record SkippedGroup(String reason, int count, List<String> examples) {
    }

    /** How a raise moves people relative to their pay band. */
    public record BandMoves(int intoBand, int intoAboveBand, int stillBelowBand) {
    }

    /**
     * {@code totalUsd} is the annual payroll increase (raise) or the total cost (bonus), at the fixed FX rates;
     * {@code totalsByCurrency} are the exact local amounts that will be saved. {@code rows} lists at most
     * {@link BulkService#PREVIEW_ROWS} people.
     */
    public record Preview(Kind kind, int matched, int affected, int scheduled, MoneyView totalUsd,
                          List<MoneyView> totalsByCurrency, BandMoves bandMoves, List<Row> rows,
                          List<SkippedGroup> skipped) {
    }

    /** {@code alreadyApplied} is true when this request id was applied before: nothing was changed again. */
    public record Result(UUID requestId, Kind kind, int applied, int skipped, boolean alreadyApplied) {
    }
}
