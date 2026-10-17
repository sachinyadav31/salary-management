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
import java.util.List;
import java.util.Locale;

/** Write side for employees. Each public method is one transaction. */
@Service
@Transactional
public class EmployeeService {

    private final EmployeeRepository employees;
    private final EmployeeEventRepository events;
    private final CountryRepository countries;
    private final DepartmentRepository departments;
    private final CurrencyConverter converter;
    private final ApplicationEventPublisher publisher;
    private final CurrentUser currentUser;
    private final Clock clock;

    public EmployeeService(EmployeeRepository employees, EmployeeEventRepository events, CountryRepository countries,
                           DepartmentRepository departments,
                           CurrencyConverter converter, ApplicationEventPublisher publisher, CurrentUser currentUser,
                           Clock clock) {
        this.employees = employees;
        this.events = events;
        this.countries = countries;
        this.departments = departments;
        this.converter = converter;
        this.publisher = publisher;
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

        Employee employee = new Employee(nextEmployeeCode(), cmd.firstName().trim(),
                cmd.lastName().trim(), email, country, department, cmd.jobTitle().trim(), cmd.level(),
                cmd.hireDate(), salary, converter.toUsd(salary), now);
        // Set before saving: one INSERT at version 0 (setting it after would dirty the new row and bump the version).
        employee.changeVariableTarget(VariableTarget.defaultFor(department.getName(), cmd.level()), now);
        employee.editedBy(currentUser.reference().orElse(null));
        employees.save(employee);
        publisher.publishEvent(new EmployeeHired(employee, salary, now));
        publisher.publishEvent(new WorkforceChanged("employee hired"));
        return employee.getId();
    }

    public void updateProfile(long id, UpdateProfile cmd) {
        Employee employee = findForUpdate(id, cmd.expectedVersion());
        String email = normaliseEmail(cmd.email());
        if (employees.existsByEmailAndIdNot(email, id)) {
            throw new ConflictException("An employee with email " + email + " already exists");
        }
        CareerChanges.Snapshot before = snapshot(employee);
        employee.updateProfile(cmd.firstName().trim(), cmd.lastName().trim(), email,
                department(cmd.departmentId()), cmd.jobTitle().trim(), cmd.level(), clock.instant());
        if (cmd.variableTargetPercent() != null) {
            employee.changeVariableTarget(VariableTarget.ofPercent(cmd.variableTargetPercent()), clock.instant());
        }
        employee.editedBy(currentUser.reference().orElse(null));
        recordCareerEvents(employee, before);
        publisher.publishEvent(new WorkforceChanged("profile updated"));
    }

    public void changeStatus(long id, EmployeeStatus status, long expectedVersion) {
        Employee employee = findForUpdate(id, expectedVersion);
        CareerChanges.Snapshot before = snapshot(employee);
        employee.changeStatus(status, clock.instant());
        employee.editedBy(currentUser.reference().orElse(null));
        recordCareerEvents(employee, before);
        publisher.publishEvent(new WorkforceChanged("status changed"));
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

    /** Career history, newest first. Mapped inside the transaction (open-in-view is off). */
    @Transactional(readOnly = true)
    public List<CareerEventView> careerEvents(long id) {
        if (!employees.existsById(id)) {
            throw new NotFoundException("Employee " + id + " not found");
        }
        return events.findByEmployeeIdOrderByOccurredAtDescIdAsc(id).stream()
                .map(e -> new CareerEventView(e.getId(), e.getType(), e.getFromValue(), e.getToValue(), e.getOccurredAt(),
                        e.getRecordedBy() == null ? null : e.getRecordedBy().getFullName()))
                .toList();
    }

    private void recordCareerEvents(Employee employee, CareerChanges.Snapshot before) {
        Instant now = clock.instant();
        var actor = currentUser.reference().orElse(null);
        CareerChanges.between(before, snapshot(employee))
                .forEach(change -> events.save(new EmployeeEvent(employee, change, now, actor)));
    }

    private static CareerChanges.Snapshot snapshot(Employee e) {
        return new CareerChanges.Snapshot(e.getLevel(), e.getJobTitle(), e.getDepartment().getName(), e.getStatus(),
                e.getVariableTarget());
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
