package com.acme.salary.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Who is making the current request, for audit. Empty for system work (seeding, scheduled rollover). */
@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    /** A lazy reference to the signed-in user, for audit columns; empty for system work. */
    public Optional<AppUser> reference() {
        return id().map(users::getReferenceById);
    }

    public Optional<Long> id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? Optional.of(user.id())
                : Optional.empty();
    }
}
