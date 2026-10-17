package com.acme.salary.bulk;

import com.acme.salary.auth.CurrentUser;
import com.acme.salary.bulk.BulkModels.BandMoves;
import com.acme.salary.bulk.BulkModels.Preview;
import com.acme.salary.bulk.BulkModels.Result;
import com.acme.salary.bulk.BulkModels.Row;
import com.acme.salary.bulk.BulkModels.SkippedGroup;
import com.acme.salary.bulk.BulkPlanner.Plan;
import com.acme.salary.bulk.BulkPlanner.PlannedBonus;
import com.acme.salary.bulk.BulkPlanner.PlannedRaise;
import com.acme.salary.bulk.BulkPlanner.Request;
import com.acme.salary.bulk.BulkPlanner.Skipped;
import com.acme.salary.bulk.BulkPlanner.Target;
import com.acme.salary.common.Money;
import com.acme.salary.common.web.MoneyView;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.WorkforceChanged;
import com.acme.salary.employee.query.EmployeeQueryRepository;
import com.acme.salary.employee.query.EmployeeSearchCriteria;
import com.acme.salary.employee.query.EmployeeSummary;
import com.acme.salary.payband.BandPosition;
import com.acme.salary.reference.CurrencyConverter;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.salary.SalaryHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Bulk raises and bonuses for everyone matching the directory filters. Preview and apply run the same plan
 * ({@link BulkPlanner}) on the same data, so the preview shows exactly what will be saved. Apply re-plans inside its
 * own transaction (nothing from the preview is trusted), writes everything with JDBC batches in one transaction, and is
 * idempotent per request id.
 */
@Service
public class BulkService {

    static final int PREVIEW_ROWS = 200;
    private static final int EXAMPLES_PER_GROUP = 3;
    private static final int IDS_PER_QUERY = 900;

    private static final Logger log = LoggerFactory.getLogger(BulkService.class);

    private final EmployeeQueryRepository directory;
    private final NamedParameterJdbcTemplate jdbc;
    private final CurrencyConverter converter;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BulkService(EmployeeQueryRepository directory, NamedParameterJdbcTemplate jdbc, CurrencyConverter converter,
                       CurrentUser currentUser, ApplicationEventPublisher events, Clock clock) {
        this.directory = directory;
        this.jdbc = jdbc;
        this.converter = converter;
        this.currentUser = currentUser;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Preview preview(EmployeeSearchCriteria criteria, Request request) {
        LocalDate today = LocalDate.now(clock);
        List<Target> targets = targets(criteria);
        return toPreview(request, targets.size(), BulkPlanner.plan(targets, request, today), today);
    }

    @Transactional
    public Result apply(EmployeeSearchCriteria criteria, Request request, UUID requestId) {
        Optional<Result> previous = previousResult(requestId);
        if (previous.isPresent()) {
            return previous.get();
        }
        LocalDate today = LocalDate.now(clock);
        Plan plan = BulkPlanner.plan(targets(criteria), request, today);
        if (plan.affected() == 0) {
            throw new IllegalArgumentException("Nobody in this selection can get this change");
        }
        String now = clock.instant().toString();
        Long userId = currentUser.id().orElse(null);
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();

        if (request.kind() == BulkPlanner.Kind.RAISE) {
            writeRaises(plan.raises(), request.date(), request.reason(), note, userId, now);
        } else {
            writeBonuses(plan.bonuses(), request, note, userId, now);
        }
        jdbc.update("""
                insert into bulk_change (request_id, kind, applied, skipped, created_at, created_by_user_id)
                values (:id, :kind, :applied, :skipped, :now, :userId)""",
                new MapSqlParameterSource()
                        .addValue("id", requestId.toString()).addValue("kind", request.kind().name())
                        .addValue("applied", plan.affected()).addValue("skipped", plan.skipped().size())
                        .addValue("now", now).addValue("userId", userId));
        events.publishEvent(new WorkforceChanged("bulk " + request.kind().name().toLowerCase()));
        log.info("Bulk {} {} applied to {} employee(s), {} skipped", request.kind(), requestId, plan.affected(),
                plan.skipped().size());
        return new Result(requestId, request.kind(), plan.affected(), plan.skipped().size(), false);
    }

    private void writeRaises(List<PlannedRaise> raises, LocalDate date, SalaryChangeReason reason, String note,
                             Long userId, String now) {
        List<Object[]> records = new ArrayList<>(raises.size());
        List<Object[]> employees = new ArrayList<>(raises.size());
        for (PlannedRaise r : raises) {
            records.add(new Object[]{r.target().employeeId(), r.to().minorUnits(), r.to().currencyCode(),
                    date.toString(), reason.name(), note, userId, now});
            // Every raise bumps the version (like a single change), so an open drawer can't overwrite it unseen.
            // A scheduled raise leaves today's salary as it is; the rollover job applies it on its date.
            employees.add(new Object[]{r.currentAfter().minorUnits(), converter.toUsd(r.currentAfter()).minorUnits(),
                    now, userId, r.target().employeeId()});
        }
        jdbc.getJdbcOperations().batchUpdate("""
                insert into salary_record (employee_id, amount_minor, currency_code, effective_date, reason, note,
                                           recorded_by_user_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)""", records);
        jdbc.getJdbcOperations().batchUpdate("""
                update employee set current_salary_minor = ?, current_salary_usd_minor = ?, updated_at = ?,
                                    updated_by_user_id = coalesce(?, updated_by_user_id), version = version + 1
                where id = ?""", employees);
    }

    private void writeBonuses(List<PlannedBonus> bonuses, Request request, String note, Long userId, String now) {
        List<Object[]> rows = new ArrayList<>(bonuses.size());
        for (PlannedBonus b : bonuses) {
            rows.add(new Object[]{b.target().employeeId(), request.bonusType().name(), b.amount().minorUnits(),
                    b.amount().currencyCode(), request.date().toString(), note, userId, now});
        }
        // Bonuses don't modify the employee row, so no version bump (same as a single award).
        jdbc.getJdbcOperations().batchUpdate("""
                insert into bonus_award (employee_id, type, amount_minor, currency_code, awarded_on, note,
                                         recorded_by_user_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)""", rows);
    }

    private Optional<Result> previousResult(UUID requestId) {
        return jdbc.query("select kind, applied, skipped from bulk_change where request_id = :id",
                Map.of("id", requestId.toString()),
                (rs, i) -> new Result(requestId, BulkPlanner.Kind.valueOf(rs.getString("kind")), rs.getInt("applied"),
                        rs.getInt("skipped"), true)).stream().findFirst();
    }

    /** Everyone matching the filters (paging ignored), with their salary history: two queries per 900 people. */
    private List<Target> targets(EmployeeSearchCriteria criteria) {
        List<EmployeeSummary> selected = new ArrayList<>();
        directory.stream(criteria, selected::add);
        Map<Long, List<SalaryHistory.Entry>> histories = histories(selected.stream().map(EmployeeSummary::id).toList());
        List<Target> targets = new ArrayList<>(selected.size());
        for (EmployeeSummary e : selected) {
            Money salary = Money.ofMinor(e.salary().minorUnits(), e.salary().currency());
            targets.add(new Target(e.id(), e.employeeCode(), e.fullName(), e.status() == EmployeeStatus.ACTIVE,
                    e.hireDate(), salary,
                    e.payBand() == null ? null : toMoney(e.payBand().min()),
                    e.payBand() == null ? null : toMoney(e.payBand().max()),
                    new SalaryHistory(e.hireDate(), histories.getOrDefault(e.id(), List.of()))));
        }
        return targets;
    }

    private Map<Long, List<SalaryHistory.Entry>> histories(List<Long> ids) {
        Map<Long, List<SalaryHistory.Entry>> byEmployee = new HashMap<>();
        for (int from = 0; from < ids.size(); from += IDS_PER_QUERY) {
            List<Long> chunk = ids.subList(from, Math.min(ids.size(), from + IDS_PER_QUERY));
            jdbc.query("""
                    select employee_id, id, amount_minor, currency_code, effective_date, reason
                    from salary_record where employee_id in (:ids)""", Map.of("ids", chunk), rs -> {
                byEmployee.computeIfAbsent(rs.getLong("employee_id"), k -> new ArrayList<>()).add(new SalaryHistory.Entry(
                        rs.getLong("id"), Money.ofMinor(rs.getLong("amount_minor"), rs.getString("currency_code")),
                        LocalDate.parse(rs.getString("effective_date")), SalaryChangeReason.valueOf(rs.getString("reason")),
                        null, null));
            });
        }
        return byEmployee;
    }

    private Preview toPreview(Request request, int matched, Plan plan, LocalDate today) {
        Map<String, Long> byCurrency = new TreeMap<>();
        long totalUsd = 0;
        List<Row> rows = new ArrayList<>();
        int intoBand = 0, intoAbove = 0, stillBelow = 0, scheduled = 0;
        for (PlannedRaise r : plan.raises()) {
            long increase = r.to().minorUnits() - r.from().minorUnits();
            byCurrency.merge(r.to().currencyCode(), increase, Long::sum);
            totalUsd += converter.toUsd(r.to()).minorUnits() - converter.toUsd(r.from()).minorUnits();
            if (request.date().isAfter(today)) {
                scheduled++;
            }
            if (r.before() != null) {
                if (r.before() == BandPosition.BELOW && r.after() == BandPosition.WITHIN) {
                    intoBand++;
                } else if (r.before() != BandPosition.ABOVE && r.after() == BandPosition.ABOVE) {
                    intoAbove++;
                } else if (r.after() == BandPosition.BELOW) {
                    stillBelow++;
                }
            }
            if (rows.size() < PREVIEW_ROWS) {
                rows.add(new Row(r.target().employeeId(), r.target().employeeCode(), r.target().name(),
                        MoneyView.from(r.from()), MoneyView.from(r.to()), null, r.before(), r.after()));
            }
        }
        for (PlannedBonus b : plan.bonuses()) {
            byCurrency.merge(b.amount().currencyCode(), b.amount().minorUnits(), Long::sum);
            totalUsd += converter.toUsd(b.amount()).minorUnits();
            if (rows.size() < PREVIEW_ROWS) {
                rows.add(new Row(b.target().employeeId(), b.target().employeeCode(), b.target().name(),
                        MoneyView.from(b.target().currentSalary()), null, MoneyView.from(b.amount()), null, null));
            }
        }
        return new Preview(request.kind(), matched, plan.affected(), scheduled,
                MoneyView.from(Money.ofMinor(totalUsd, CurrencyConverter.REPORTING_CURRENCY)),
                byCurrency.entrySet().stream().map(e -> MoneyView.from(Money.ofMinor(e.getValue(), e.getKey()))).toList(),
                request.kind() == BulkPlanner.Kind.RAISE ? new BandMoves(intoBand, intoAbove, stillBelow) : null,
                rows, skippedGroups(plan.skipped()));
    }

    private static List<SkippedGroup> skippedGroups(List<Skipped> skipped) {
        Map<String, List<String>> byReason = new LinkedHashMap<>();
        for (Skipped s : skipped) {
            byReason.computeIfAbsent(s.reason(), k -> new ArrayList<>()).add(s.target().name());
        }
        return byReason.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .map(e -> new SkippedGroup(e.getKey(), e.getValue().size(),
                        e.getValue().subList(0, Math.min(EXAMPLES_PER_GROUP, e.getValue().size()))))
                .toList();
    }

    private static Money toMoney(MoneyView view) {
        return Money.ofMinor(view.minorUnits(), view.currency());
    }
}
