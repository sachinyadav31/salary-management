package com.acme.salary.employee;

import com.acme.salary.auth.AppUser;
import com.acme.salary.common.ConflictException;
import com.acme.salary.common.Money;
import com.acme.salary.reference.Country;
import com.acme.salary.reference.Department;
import com.acme.salary.reference.JobLevel;
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
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String employeeCode;
    private String firstName;
    private String lastName;
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_code")
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    private String jobTitle;

    @Enumerated(EnumType.STRING)
    private JobLevel level;

    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    private EmployeeStatus status;

    private long currentSalaryMinor;
    private String currentSalaryCurrency;
    private long currentSalaryUsdMinor;

    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private long version;

    /** Variable pay target in basis points of base salary (1000 = 10%). */
    private int variableTargetBp;

    /** Who last edited this employee (null: created by the seed or changed only by system jobs). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private AppUser updatedBy;

    protected Employee() {
    }

    public Employee(String employeeCode, String firstName, String lastName, String email, Country country,
                    Department department, String jobTitle, JobLevel level, LocalDate hireDate,
                    Money currentSalary, Money currentSalaryUsd, Instant now) {
        requireSalaryInCountryCurrency(currentSalary, country);
        this.employeeCode = employeeCode;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.country = country;
        this.department = department;
        this.jobTitle = jobTitle;
        this.level = level;
        this.hireDate = hireDate;
        this.status = EmployeeStatus.ACTIVE;
        this.currentSalaryMinor = currentSalary.minorUnits();
        this.currentSalaryCurrency = currentSalary.currencyCode();
        this.currentSalaryUsdMinor = currentSalaryUsd.minorUnits();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateProfile(String firstName, String lastName, String email, Department department,
                              String jobTitle, JobLevel level, Instant now) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.department = department;
        this.jobTitle = jobTitle;
        this.level = level;
        this.updatedAt = now;
    }

    public void changeStatus(EmployeeStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
    }

    /**
     * Sets the salary currently in effect (local + USD). Called when a change takes effect,
     * either immediately or later via the rollover job.
     */
    public void applySalary(Money salary, Money salaryUsd, Instant now) {
        requireSalaryInCountryCurrency(salary, country);
        this.currentSalaryMinor = salary.minorUnits();
        this.currentSalaryCurrency = salary.currencyCode();
        this.currentSalaryUsdMinor = salaryUsd.minorUnits();
        this.updatedAt = now;
    }

    /**
     * Records that the employee changed through a related record (e.g. a scheduled salary change).
     * Returns false if the timestamp was already {@code now}, i.e. nothing on this row changed.
     */
    public boolean touch(Instant now) {
        if (now.equals(updatedAt)) {
            return false;
        }
        this.updatedAt = now;
        return true;
    }

    /** Rejects writes based on a stale copy of this employee. */
    public void assertVersion(long expectedVersion) {
        if (version != expectedVersion) {
            throw new ConflictException("Employee " + id + " was changed by someone else. Reload and try again.");
        }
    }

    public VariableTarget getVariableTarget() {
        return new VariableTarget(variableTargetBp);
    }

    public void changeVariableTarget(VariableTarget target, Instant now) {
        this.variableTargetBp = target.basisPoints();
        this.updatedAt = now;
    }

    /** Records who made a user-initiated change (system jobs leave the last editor unchanged). */
    public void editedBy(AppUser user) {
        if (user != null) {
            this.updatedBy = user;
        }
    }

    public boolean isActive() {
        return status == EmployeeStatus.ACTIVE;
    }

    private static void requireSalaryInCountryCurrency(Money salary, Country country) {
        if (!salary.isPositive()) {
            throw new IllegalArgumentException("Salary must be positive");
        }
        if (!salary.currencyCode().equals(country.getCurrencyCode())) {
            throw new IllegalArgumentException("Salary currency %s does not match %s (%s)"
                    .formatted(salary.currencyCode(), country.getName(), country.getCurrencyCode()));
        }
    }

    public long getVersion() {
        return version;
    }

    public Money getCurrentSalary() {
        return Money.ofMinor(currentSalaryMinor, currentSalaryCurrency);
    }

    public Money getCurrentSalaryUsd() {
        return Money.ofMinor(currentSalaryUsdMinor, "USD");
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public Country getCountry() {
        return country;
    }

    public Department getDepartment() {
        return department;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public JobLevel getLevel() {
        return level;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
