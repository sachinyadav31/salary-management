package com.acme.salary.auth;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after {@link #MAX_FAILURES} consecutive failures an email is locked for
 * {@link #LOCK_DURATION}. In memory, so it resets on restart and is per instance; enough for one instance.
 * Trade-off: a per-account lock lets someone lock a known user out for a few minutes; production would add
 * per-IP limits or CAPTCHA, or rely on SSO.
 */
@Component
public class LoginAttempts {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(5);

    private record State(int failures, Instant lockedUntil) {
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttempts(Clock clock) {
        this.clock = clock;
    }

    /** How long the email is still locked, if it is. */
    public Optional<Duration> lockedFor(String email) {
        State state = states.get(key(email));
        if (state == null || state.lockedUntil() == null) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(clock.instant(), state.lockedUntil());
        return remaining.isNegative() || remaining.isZero() ? Optional.empty() : Optional.of(remaining);
    }

    public void recordFailure(String email) {
        states.compute(key(email), (k, state) -> {
            int failures = (state == null || isExpiredLock(state) ? 0 : state.failures()) + 1;
            return new State(failures, failures >= MAX_FAILURES ? clock.instant().plus(LOCK_DURATION) : null);
        });
    }

    public void recordSuccess(String email) {
        states.remove(key(email));
    }

    private boolean isExpiredLock(State state) {
        return state.lockedUntil() != null && !state.lockedUntil().isAfter(clock.instant());
    }

    private static String key(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
