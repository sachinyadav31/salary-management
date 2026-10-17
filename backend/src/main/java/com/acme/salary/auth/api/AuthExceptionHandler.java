package com.acme.salary.auth.api;

import com.acme.salary.auth.api.AuthController.InvalidCredentialsException;
import com.acme.salary.auth.api.AuthController.TooManyAttemptsException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problem.setTitle("Sign-in failed");
        return problem;
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    ResponseEntity<ProblemDetail> tooManyAttempts(TooManyAttemptsException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        problem.setTitle("Temporarily locked");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                .body(problem);
    }
}
