package com.acme.salary.seed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Seeds demo data on startup when {@code app.seed.enabled=true} (the default for local and demo runs). */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SeedOnStartup implements ApplicationRunner {

    private final DatabaseSeeder seeder;
    private final int count;
    private final long seed;
    private final LocalDate asOf;

    public SeedOnStartup(DatabaseSeeder seeder,
                         @Value("${app.seed.count}") int count,
                         @Value("${app.seed.random-seed}") long seed,
                         @Value("${app.seed.as-of}") LocalDate asOf) {
        this.seeder = seeder;
        this.count = count;
        this.seed = seed;
        this.asOf = asOf;
    }

    @Override
    public void run(ApplicationArguments args) {
        seeder.seedIfEmpty(new EmployeeSeedGenerator(seed, asOf).generate(count));
    }
}
