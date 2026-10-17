package com.acme.salary.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * The signed-in user as stored in the HTTP session. Holds only what the app needs (id for audit,
 * name for display); the password hash is not kept after authentication.
 */
public final class AuthenticatedUser implements UserDetails, Serializable {

    /** Single persona in the brief: every signed-in user is an HR admin. */
    public static final String ROLE_HR = "ROLE_HR";

    private final long id;
    private final String email;
    private final String fullName;
    private final transient String passwordHash;
    private final boolean enabled;

    public AuthenticatedUser(long id, String email, String fullName, String passwordHash, boolean enabled) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    static AuthenticatedUser from(AppUser user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getFullName(), user.getPasswordHash(), user.isEnabled());
    }

    public long id() {
        return id;
    }

    public String fullName() {
        return fullName;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE_HR));
    }
}
