package com.acme.salary.salary;

import com.acme.salary.auth.CurrentUser;
import com.acme.salary.common.ConflictException;
import com.acme.salary.common.Money;
import com.acme.salary.common.NotFoundException;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeHired;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.WorkforceChanged;
import com.acme.salary.reference.CurrencyConverter;
import com.acme.salary.salary.SalaryHistory.Entry;
import com.acme.salary.salary.SalaryHistory.NewChange;
import com.acme.salary.salary.SalaryHistory.TimelineEntry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Write side for salary history. Keeps the employee's denormalised current salary in sync. */
@Service
@Transactional
public class SalaryService {

    private static final Logger log = LoggerFactory.getLogger(SalaryService.class);

    public record RecordChange(BigDecimal amount, LocalDate effectiveDate, SalaryChangeReason reason, String note,
                               long expectedVersion) {
    }

    private final EmployeeRepository employees;
    private final SalaryRecordRepository salaryRecords;
    private final CurrencyConverter converter;
    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager em;
    private final ApplicationEventPublisher events;
    private final CurrentUser currentUser;
    private final Clock clock;

    public SalaryService(EmployeeRepository employees, SalaryRecordRepository salaryRecords,
                         CurrencyConverter converter, NamedParameterJdbcTemplate jdbc, EntityManager em,
                         ApplicationEventPublisher events, CurrentUser currentUser, Clock clock) {
        this.employees = employees;
        this.salaryRecords = salaryRecords;
        this.converter = converter;
        this.jdbc = jdbc;
        this.em = em;
        this.events = events;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** Synchronous listener: runs inside the employee-creation transaction, so hire + HIRE record are atomic. */
    @EventListener
    public void onEmployeeHired(EmployeeHired event) {
        Employee employee = event.employee();
        salaryRecords.save(new SalaryRecord(employee, event.startingSalary(), employee.getHireDate(),
                SalaryChangeReason.HIRE, null, event.at(), currentUser.reference().orElse(null)));
    }

    /**
     * Appends a salary change. If it is already in effect, the employee's current salary (local + USD)
     * is updated in the same transaction; a future-dated change waits for {@link #applyDueChanges}.
     */
    public long recordChange(long employeeId, RecordChange cmd) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee " + employeeId + " not found"));
        employee.assertVersion(cmd.expectedVersion());
        if (!employee.isActive()) {
            throw new ConflictException("Employee " + employeeId + " is inactive; reactivate them first");
        }
        Money amount = Money.of(cmd.amount(), employee.getCountry().getCurrencyCode());
        String note = cmd.note() == null || cmd.note().isBlank() ? null : cmd.note().trim();
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();

        historyOf(employee).checkCanAdd(new NewChange(amount, cmd.effectiveDate(), cmd.reason(), note), today);
        SalaryRecord saved = salaryRecords.save(
                new SalaryRecord(employee, amount, cmd.effectiveDate(), cmd.reason(), note, now,
                        currentUser.reference().orElse(null)));

        boolean salaryApplied = syncCurrentSalary(employee, today, now);
        boolean timestampChanged = employee.touch(now);
        employee.editedBy(currentUser.reference().orElse(null));
        if (!salaryApplied && !timestampChanged) {
            // Nothing on the employee row changed (scheduled change, same clock tick), so dirty-checking won't
            // bump the version. Force it: one salary change = exactly one version bump.
            em.lock(employee, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        events.publishEvent(new WorkforceChanged("salary changed"));
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(long employeeId) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee " + employeeId + " not found"));
        return historyOf(employee).timeline(LocalDate.now(clock));
    }

    /**
     * Rollover: promotes scheduled changes whose effective date has arrived. Finds employees whose stored
     * current salary differs from the latest record in effect and re-syncs them. Idempotent and self-healing.
     */
    public int applyDueChanges(LocalDate today) {
        List<Long> stale = jdbc.queryForList("""
                select e.id from employee e
                where e.current_salary_minor <> (
                    select s.amount_minor from salary_record s
                    where s.employee_id = e.id and s.effective_date <= :today
                    order by s.effective_date desc, s.id desc limit 1)""",
                Map.of("today", today.toString()), Long.class);
        Instant now = clock.instant();
        for (Long id : stale) {
            syncCurrentSalary(employees.findById(id).orElseThrow(), today, now);
        }
        if (!stale.isEmpty()) {
            log.info("Salary rollover applied {} due change(s) as of {}", stale.size(), today);
            events.publishEvent(new WorkforceChanged("scheduled salary changes applied"));
        }
        return stale.size();
    }

    /** Returns true if the employee's current salary changed. */
    private boolean syncCurrentSalary(Employee employee, LocalDate today, Instant now) {
        return historyOf(employee).currentAsOf(today)
                .map(Entry::amount)
                .filter(inEffect -> !inEffect.equals(employee.getCurrentSalary()))
                .map(inEffect -> {
                    employee.applySalary(inEffect, converter.toUsd(inEffect), now);
                    return true;
                })
                .orElse(false);
    }

    private SalaryHistory historyOf(Employee employee) {
        List<Entry> entries = salaryRecords.findByEmployeeIdOrderByEffectiveDateDescIdDesc(employee.getId()).stream()
                .map(r -> new Entry(r.getId(), r.getAmount(), r.getEffectiveDate(), r.getReason(), r.getNote(),
                        r.getRecordedBy() == null ? null : r.getRecordedBy().getFullName()))
                .toList();
        return new SalaryHistory(employee.getHireDate(), entries);
    }
}
