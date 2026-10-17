package com.acme.salary.employee;

import com.acme.salary.auth.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** One career event (level/title/department/status change). Append-only. */
@Entity
@Table(name = "employee_event")
public class EmployeeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    private CareerEventType type;

    private String fromValue;
    private String toValue;
    private Instant occurredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private AppUser recordedBy;

    protected EmployeeEvent() {
    }

    public EmployeeEvent(Employee employee, CareerChanges.Change change, Instant occurredAt, AppUser recordedBy) {
        this.employee = employee;
        this.type = change.type();
        this.fromValue = change.from();
        this.toValue = change.to();
        this.occurredAt = occurredAt;
        this.recordedBy = recordedBy;
    }

    public Long getId() {
        return id;
    }

    public CareerEventType getType() {
        return type;
    }

    public String getFromValue() {
        return fromValue;
    }

    public String getToValue() {
        return toValue;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public AppUser getRecordedBy() {
        return recordedBy;
    }
}
