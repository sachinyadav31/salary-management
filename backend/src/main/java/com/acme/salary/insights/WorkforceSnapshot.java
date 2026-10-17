package com.acme.salary.insights;

import java.time.Instant;
import java.util.List;

/** An immutable point-in-time view of the active workforce, computed once and cached. */
public record WorkforceSnapshot(List<WorkforceMember> members, Instant asOf, String fxRatesAsOf) {

    public WorkforceSnapshot {
        members = List.copyOf(members);
    }
}
