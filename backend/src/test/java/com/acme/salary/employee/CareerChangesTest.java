package com.acme.salary.employee;

import com.acme.salary.employee.CareerChanges.Change;
import com.acme.salary.employee.CareerChanges.Snapshot;
import com.acme.salary.reference.JobLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CareerChangesTest {

    private final Snapshot engineer = new Snapshot(JobLevel.L2, "Software Engineer", "Engineering", EmployeeStatus.ACTIVE);

    @Test
    void noDifferenceMeansNoEvents() {
        assertThat(CareerChanges.between(engineer, engineer)).isEmpty();
    }

    @Test
    void reportsEachCareerChangeWithBeforeAndAfter() {
        Snapshot promoted = new Snapshot(JobLevel.L3, "Senior Software Engineer", "Product", EmployeeStatus.ACTIVE);

        assertThat(CareerChanges.between(engineer, promoted)).containsExactly(
                new Change(CareerEventType.LEVEL_CHANGED, "L2", "L3"),
                new Change(CareerEventType.TITLE_CHANGED, "Software Engineer", "Senior Software Engineer"),
                new Change(CareerEventType.DEPARTMENT_CHANGED, "Engineering", "Product"));
    }

    @Test
    void reportsDeactivationAndReactivation() {
        Snapshot inactive = new Snapshot(JobLevel.L2, "Software Engineer", "Engineering", EmployeeStatus.INACTIVE);

        assertThat(CareerChanges.between(engineer, inactive)).containsExactly(new Change(CareerEventType.DEACTIVATED, null, null));
        assertThat(CareerChanges.between(inactive, engineer)).containsExactly(new Change(CareerEventType.REACTIVATED, null, null));
    }
}
