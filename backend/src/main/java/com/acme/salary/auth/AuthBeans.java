package com.acme.salary.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Credential checking. Kept apart from {@link SecurityConfig} (the HTTP rules) so web slice tests can load
 * the filter chain without the user database.
 */
@Configuration
public class AuthBeans {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * DaoAuthenticationProvider also hashes a dummy password when the email is unknown, so response times
     * don't reveal which emails exist.
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
