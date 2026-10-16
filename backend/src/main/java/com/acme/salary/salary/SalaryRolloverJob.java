package com.acme.salary.salary;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Applies scheduled salary changes that have become due: daily just after midnight UTC, and once at startup
 * so a change is never missed if the server was down on its effective date.
 */
@Component
public class SalaryRolloverJob {

    private final SalaryService salaryService;
    private final Clock clock;

    public SalaryRolloverJob(SalaryService salaryService, Clock clock) {
        this.salaryService = salaryService;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
    public void run() {
        salaryService.applyDueChanges(LocalDate.now(clock));
    }
}
