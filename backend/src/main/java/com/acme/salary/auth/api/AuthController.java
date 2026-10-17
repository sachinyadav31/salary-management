package com.acme.salary.auth.api;

import com.acme.salary.auth.AuthenticatedUser;
import com.acme.salary.auth.LoginAttempts;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Session sign-in for HR staff")
public class AuthController {

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record UserView(String email, String fullName) {
        static UserView of(AuthenticatedUser user) {
            return new UserView(user.getUsername(), user.fullName());
        }
    }

    public record SessionView(boolean authenticated, UserView user) {
    }

    /** Wrong email and wrong password look identical, so the response doesn't reveal which emails exist. */
    public static class InvalidCredentialsException extends RuntimeException {
        InvalidCredentialsException() {
            super("Invalid email or password.");
        }
    }

    public static class TooManyAttemptsException extends RuntimeException {
        private final long retryAfterSeconds;

        TooManyAttemptsException(long retryAfterSeconds) {
            super("Too many failed sign-in attempts. Try again in %d minute(s).".formatted(Math.max(1, (retryAfterSeconds + 59) / 60)));
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginAttempts attempts;

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository,
                          LoginAttempts attempts) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.attempts = attempts;
    }

    /**
     * Who is signed in, if anyone. Public; the SPA calls it on start-up. Reading the CSRF token here makes
     * Spring write the XSRF-TOKEN cookie (tokens are otherwise deferred), so the cookie exists before the first
     * POST (the login itself).
     */
    @GetMapping("/session")
    @Operation(summary = "Current session (also issues the CSRF cookie)")
    public SessionView session(CsrfToken csrfToken, @AuthenticationPrincipal AuthenticatedUser user) {
        csrfToken.getToken();
        return user == null ? new SessionView(false, null) : new SessionView(true, UserView.of(user));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in; starts a session (HttpOnly cookie)")
    public UserView login(@Valid @RequestBody LoginRequest login, HttpServletRequest request, HttpServletResponse response) {
        String email = login.email().trim().toLowerCase(Locale.ROOT);
        attempts.lockedFor(email).ifPresent(remaining -> {
            // Round up: Retry-After must never tell a client to come back while it is still locked.
            throw new TooManyAttemptsException((remaining.toMillis() + 999) / 1000);
        });

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, login.password()));
        } catch (AuthenticationException e) {
            attempts.recordFailure(email);
            throw new InvalidCredentialsException();
        }
        attempts.recordSuccess(email);

        // A new session id on sign-in prevents session fixation (an attacker planting a known session id).
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return UserView.of((AuthenticatedUser) authentication.getPrincipal());
    }
}
