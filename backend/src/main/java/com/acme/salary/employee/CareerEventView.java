package com.acme.salary.employee;

import java.time.Instant;

/** A career event for display; {@code recordedBy} is a name, or null for system changes. */
public record CareerEventView(long id, CareerEventType type, String from, String to, Instant occurredAt,
                              String recordedBy) {
}
