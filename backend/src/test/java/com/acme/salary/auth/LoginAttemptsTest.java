package com.acme.salary.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptsTest {

    /** A clock the test can move forward. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-17T10:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttempts attempts = new LoginAttempts(clock);

    @Test
    void locksAfterFiveConsecutiveFailures() {
        for (int i = 0; i < 4; i++) attempts.recordFailure("ada@acme.test");
        assertThat(attempts.lockedFor("ada@acme.test")).isEmpty();

        attempts.recordFailure("ada@acme.test");

        assertThat(attempts.lockedFor("ada@acme.test")).contains(Duration.ofMinutes(5));
    }

    @Test
    void unlocksWhenTheLockExpiresAndCountsAfreshAfterwards() {
        for (int i = 0; i < 5; i++) attempts.recordFailure("ada@acme.test");

        clock.now = clock.now.plus(Duration.ofMinutes(5));
        assertThat(attempts.lockedFor("ada@acme.test")).isEmpty();

        attempts.recordFailure("ada@acme.test");
        assertThat(attempts.lockedFor("ada@acme.test")).as("one failure after expiry is not a new lock").isEmpty();
    }

    @Test
    void successResetsTheCount() {
        for (int i = 0; i < 4; i++) attempts.recordFailure("ada@acme.test");
        attempts.recordSuccess("ada@acme.test");
        for (int i = 0; i < 4; i++) attempts.recordFailure("ada@acme.test");

        assertThat(attempts.lockedFor("ada@acme.test")).isEmpty();
    }

    @Test
    void emailsAreCaseInsensitive() {
        for (int i = 0; i < 5; i++) attempts.recordFailure(" Ada@ACME.test ");

        assertThat(attempts.lockedFor("ada@acme.test")).isPresent();
    }
}
