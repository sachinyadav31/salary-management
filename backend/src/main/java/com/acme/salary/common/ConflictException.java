package com.acme.salary.common;

/** The request conflicts with current state (duplicate, stale version). Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
