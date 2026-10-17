package com.acme.salary.bonus;

import com.acme.salary.auth.CurrentUser;
import com.acme.salary.common.ConflictException;
import com.acme.salary.common.Money;
import com.acme.salary.common.NotFoundException;
import com.acme.salary.common.web.MoneyView;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.WorkforceChanged;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Awards one-off bonuses. No optimistic-lock version is needed: an award doesn't modify the employee, so two
 * people awarding bonuses at once can't overwrite each other; both awards are recorded.
 */
@Service
@Transactional
public class BonusService {

    public record AwardBonus(BonusType type, BigDecimal amount, LocalDate awardedOn, String note) {
    }

    /** {@code recordedBy} is a name, or null for system-generated awards (seed data). */
    public record BonusView(long id, BonusType type, MoneyView amount, LocalDate awardedOn, String note, String recordedBy) {
    }

    private final EmployeeRepository employees;
    private final BonusAwardRepository awards;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BonusService(EmployeeRepository employees, BonusAwardRepository awards, CurrentUser currentUser,
                        ApplicationEventPublisher events, Clock clock) {
        this.employees = employees;
        this.awards = awards;
        this.currentUser = currentUser;
        this.events = events;
        this.clock = clock;
    }

    public long award(long employeeId, AwardBonus cmd) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee " + employeeId + " not found"));
        if (!employee.isActive()) {
            throw new ConflictException("Employee " + employeeId + " is inactive; reactivate them first");
        }
        String currency = employee.getCountry().getCurrencyCode();
        String note = cmd.note() == null || cmd.note().isBlank() ? null : cmd.note().trim();
        BonusRules.NewBonus bonus = new BonusRules.NewBonus(cmd.type(), Money.of(cmd.amount(), currency), cmd.awardedOn(), note);
        BonusRules.check(bonus, currency, employee.getHireDate(), LocalDate.now(clock));

        BonusAward saved = awards.save(new BonusAward(employee, bonus, clock.instant(), currentUser.reference().orElse(null)));
        events.publishEvent(new WorkforceChanged("bonus awarded"));
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public List<BonusView> bonuses(long employeeId) {
        if (!employees.existsById(employeeId)) {
            throw new NotFoundException("Employee " + employeeId + " not found");
        }
        return awards.findByEmployeeIdOrderByAwardedOnDescIdDesc(employeeId).stream()
                .map(b -> new BonusView(b.getId(), b.getType(), MoneyView.from(b.getAmount()), b.getAwardedOn(), b.getNote(),
                        b.getRecordedBy() == null ? null : b.getRecordedBy().getFullName()))
                .toList();
    }
}
