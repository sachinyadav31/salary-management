package com.acme.salary.salary;

import com.acme.salary.auth.AppUser;
import com.acme.salary.common.Money;
import com.acme.salary.employee.Employee;
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
import java.time.LocalDate;

/** One entry in an employee's append-only salary history. Never updated after insert. */
@Entity
@Table(name = "salary_record")
public class SalaryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private long amountMinor;
    private String currencyCode;
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    private SalaryChangeReason reason;

    private String note;
    private Instant createdAt;

    /** Who recorded this entry; null for system-generated history (seed data). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private AppUser recordedBy;

    protected SalaryRecord() {
    }

    public SalaryRecord(Employee employee, Money amount, LocalDate effectiveDate, SalaryChangeReason reason,
                        String note, Instant createdAt, AppUser recordedBy) {
        this.employee = employee;
        this.amountMinor = amount.minorUnits();
        this.currencyCode = amount.currencyCode();
        this.effectiveDate = effectiveDate;
        this.reason = reason;
        this.note = note;
        this.createdAt = createdAt;
        this.recordedBy = recordedBy;
    }

    public AppUser getRecordedBy() {
        return recordedBy;
    }

    public Money getAmount() {
        return Money.ofMinor(amountMinor, currencyCode);
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public SalaryChangeReason getReason() {
        return reason;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
