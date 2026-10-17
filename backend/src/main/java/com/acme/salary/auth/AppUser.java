package com.acme.salary.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An HR user who can sign in. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String fullName;
    private String passwordHash;
    private boolean enabled;
    private Instant createdAt;

    protected AppUser() {
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
