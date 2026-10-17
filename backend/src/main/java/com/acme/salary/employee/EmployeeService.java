package com.acme.salary.employee;

import com.acme.salary.auth.CurrentUser;
import com.acme.salary.common.ConflictException;
import com.acme.salary.common.Money;
import com.acme.salary.common.NotFoundException;
import com.acme.salary.employee.EmployeeCommands.CreateEmployee;
import com.acme.salary.employee.EmployeeCommands.UpdateProfile;
import com.acme.salary.reference.Country;
import com.acme.salary.reference.CountryRepository;
import com.acme.salary.reference.CurrencyConverter;
import com.acme.salary.reference.Department;
import com.acme.salary.reference.DepartmentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/** Write side for employees. Each public method is one transaction. */
@Service
@Transactional
public class EmployeeService {

    private final EmployeeRepository employees;
    private final CountryRepository countries;
    private final DepartmentRepository departments;
    private final CurrencyConverter converter;
    private final ApplicationEventPublisher events;
    private final CurrentUser currentUser;
    private final Clock clock;

    public EmployeeService(EmployeeRepository employees, CountryRepository countries, DepartmentRepository departments,
                           CurrencyConverter converter, ApplicationEventPublisher events, CurrentUser currentUser,
                           Clock clock) {
        this.employees = employees;
        this.countries = countries;
        this.departments = departments;
        this.converter = converter;
        this.events = events;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Creates the employee. The HIRE salary record is written by the salary feature in response to
     * {@link EmployeeHired}, inside this same transaction. Returns the new id.
     */
    public long create(CreateEmployee cmd) {
        Country country = countries.findById(cmd.countryCode())
                .orElseThrow(() -> new IllegalArgumentException("Unknown country: " + cmd.countryCode()));
        Department department = department(cmd.departmentId());
        String email = normaliseEmail(cmd.email());
        if (employees.existsByEmail(email)) {
            throw new ConflictException("An employee with email " + email + " already exists");
        }
        Money salary = Money.of(cmd.salaryAmount(), country.getCurrencyCode());
        Instant now = clock.instant();

        Employee employee = employees.save(new Employee(nextEmployeeCode(), cmd.firstName().trim(),
                cmd.lastName().trim(), email, country, department, cmd.jobTitle().trim(), cmd.level(),
                cmd.hireDate(), salary, converter.toUsd(salary), now));
        employee.editedBy(currentUser.reference().orElse(null));
        events.publishEvent(new EmployeeHired(employee, salary, now));
        events.publishEvent(new WorkforceChanged("employee hired"));
        return employee.getId();
    }

    public void updateProfile(long id, UpdateProfile cmd) {
        Employee employee = findForUpdate(id, cmd.expectedVersion());
        String email = normaliseEmail(cmd.email());
        if (employees.existsByEmailAndIdNot(email, id)) {
            throw new ConflictException("An employee with email " + email + " already exists");
        }
        employee.updateProfile(cmd.firstName().trim(), cmd.lastName().trim(), email,
                department(cmd.departmentId()), cmd.jobTitle().trim(), cmd.level(), clock.instant());
        employee.editedBy(currentUser.reference().orElse(null));
        events.publishEvent(new WorkforceChanged("profile updated"));
    }

    public void changeStatus(long id, EmployeeStatus status, long expectedVersion) {
        Employee employee = findForUpdate(id, expectedVersion);
        employee.changeStatus(status, clock.instant());
        employee.editedBy(currentUser.reference().orElse(null));
        events.publishEvent(new WorkforceChanged("status changed"));
    }

    /**
     * Loads the employee and rejects stale writes. JPA's @Version alone cannot catch a stale *form*,
     * because every request loads a fresh entity; comparing the client's version closes that gap.
     */
    private Employee findForUpdate(long id, long expectedVersion) {
        Employee employee = employees.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));
        employee.assertVersion(expectedVersion);
        return employee;
    }

    private Department department(long id) {
        return departments.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown department: " + id));
    }

    private String nextEmployeeCode() {
        return "E%05d".formatted(employees.maxEmployeeNumber() + 1);
    }

    static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
