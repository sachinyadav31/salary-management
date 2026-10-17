package com.acme.salary.employee;

import com.acme.salary.reference.JobLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Decides which differences between two versions of an employee are career events. Pure function.
 * Name and email edits are corrections, not career moves, so they are not events.
 */
public final class CareerChanges {

    /** The career-relevant facts about an employee at one moment. */
    public record Snapshot(JobLevel level, String jobTitle, String department, EmployeeStatus status,
                           VariableTarget variableTarget) {
    }

    public record Change(CareerEventType type, String from, String to) {
    }

    private CareerChanges() {
    }

    public static List<Change> between(Snapshot before, Snapshot after) {
        List<Change> changes = new ArrayList<>();
        if (before.level() != after.level()) {
            changes.add(new Change(CareerEventType.LEVEL_CHANGED, before.level().name(), after.level().name()));
        }
        if (!Objects.equals(before.jobTitle(), after.jobTitle())) {
            changes.add(new Change(CareerEventType.TITLE_CHANGED, before.jobTitle(), after.jobTitle()));
        }
        if (!Objects.equals(before.department(), after.department())) {
            changes.add(new Change(CareerEventType.DEPARTMENT_CHANGED, before.department(), after.department()));
        }
        if (!Objects.equals(before.variableTarget(), after.variableTarget())) {
            changes.add(new Change(CareerEventType.VARIABLE_TARGET_CHANGED, before.variableTarget().label(),
                    after.variableTarget().label()));
        }
        if (before.status() != after.status()) {
            changes.add(new Change(after.status() == EmployeeStatus.ACTIVE ? CareerEventType.REACTIVATED
                    : CareerEventType.DEACTIVATED, null, null));
        }
        return changes;
    }
}
