package com.acme.salary.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;

/**
 * Session-cookie authentication for a same-origin SPA (deliberately not JWT):
 * <ul>
 *   <li>The session id lives in an HttpOnly cookie, so JavaScript (and therefore XSS) can't read it, and
 *       logout really ends the session server-side.</li>
 *   <li>CSRF protection in SPA mode: the token is in a readable XSRF-TOKEN cookie and Angular echoes it in the
 *       X-XSRF-TOKEN header on every write.</li>
 *   <li>API errors are RFC 7807 JSON (401 not signed in, 403 forbidden, e.g. missing CSRF token), never a
 *       redirect to an HTML login page.</li>
 * </ul>
 * The Angular shell, static assets and API docs are public; all data under /api requires a session.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/session").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.spa())
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository()))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) ->
                                problem(response, HttpStatus.UNAUTHORIZED, "Not signed in", "Sign in to continue."))
                        .accessDeniedHandler((request, response, e) ->
                                problem(response, HttpStatus.FORBIDDEN, "Forbidden",
                                        "The request was rejected. Reload the page and try again.")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("JSESSIONID"))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    private static void problem(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}"
                .formatted(title, status.value(), detail));
    }
}
