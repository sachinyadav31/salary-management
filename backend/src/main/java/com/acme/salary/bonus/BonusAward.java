package com.acme.salary.bonus;

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

/** A one-off bonus award. Append-only; never changes base salary or compa-ratio. */
@Entity
@Table(name = "bonus_award")
public class BonusAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    private BonusType type;

    private long amountMinor;
    private String currencyCode;
    private LocalDate awardedOn;
    private String note;
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private AppUser recordedBy;

    protected BonusAward() {
    }

    public BonusAward(Employee employee, BonusRules.NewBonus bonus, Instant createdAt, AppUser recordedBy) {
        this.employee = employee;
        this.type = bonus.type();
        this.amountMinor = bonus.amount().minorUnits();
        this.currencyCode = bonus.amount().currencyCode();
        this.awardedOn = bonus.awardedOn();
        this.note = bonus.note();
        this.createdAt = createdAt;
        this.recordedBy = recordedBy;
    }

    public Long getId() {
        return id;
    }

    public BonusType getType() {
        return type;
    }

    public Money getAmount() {
        return Money.ofMinor(amountMinor, currencyCode);
    }

    public LocalDate getAwardedOn() {
        return awardedOn;
    }

    public String getNote() {
        return note;
    }

    public AppUser getRecordedBy() {
        return recordedBy;
    }
}
