package com.acme.salary.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real security filter chain against the demo users seeded by migration V6.
 * <p>
 * Writes use the real browser CSRF flow (XSRF-TOKEN cookie echoed in the X-XSRF-TOKEN header), not
 * spring-security-test's {@code csrf()} post-processor: that helper swaps the shared CSRF filter's token repository
 * for one that never writes the cookie, which would break later requests in this cached context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    static final String PRIYA = "hr.admin@acme.example";
    static final String PASSWORD = "PayAtlas!2026";

    @Autowired MockMvcTester mvc;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from salary_record where employee_id in (select id from employee where email like '%@auth.test')");
        jdbc.update("delete from employee where email like '%@auth.test'");
    }

    @Test
    void apiRequiresASessionAndAnswersWithProblemJson() {
        assertThat(mvc.get().uri("/api/employees")).hasStatus(401)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("Sign in to continue.");
    }

    @Test
    void appShellHealthAndApiDocsArePublic() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
        assertThat(mvc.get().uri("/api/auth/session")).hasStatusOk()
                .bodyJson().extractingPath("$.authenticated").isEqualTo(false);
    }

    @Test
    void signsInWithTheSpaCsrfFlowAndUsesTheSessionUntilLogout() {
        // 1. The SPA asks who is signed in; this also issues the XSRF-TOKEN cookie.
        MvcTestResult session = mvc.get().uri("/api/auth/session").exchange();
        Cookie xsrf = session.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).as("JavaScript must be able to read it").isFalse();

        // 2. Login echoes the cookie value in the header, as Angular's HttpClient does.
        MockHttpSession httpSession = new MockHttpSession();
        String idBeforeLogin = httpSession.getId();
        MvcTestResult login = mvc.post().uri("/api/auth/login").session(httpSession).cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(credentials(PRIYA, PASSWORD)).exchange();
        assertThat(login).hasStatusOk().bodyJson().extractingPath("$.fullName").isEqualTo("Priya Sharma");
        assertThat(httpSession.getId()).as("new session id on sign-in (session fixation)").isNotEqualTo(idBeforeLogin);

        // 3. The session now opens the API.
        assertThat(mvc.get().uri("/api/meta").session(httpSession)).hasStatusOk();
        assertThat(mvc.get().uri("/api/auth/session").session(httpSession))
                .bodyJson().extractingPath("$.user.email").isEqualTo(PRIYA);

        // 4. Logout ends it.
        Cookie token = xsrfCookie();
        assertThat(mvc.post().uri("/api/auth/logout").session(httpSession).cookie(token).header("X-XSRF-TOKEN", token.getValue()))
                .hasStatus(204);
        assertThat(mvc.get().uri("/api/meta").session(httpSession)).hasStatus(401);
    }

    @Test
    void writesWithoutACsrfTokenAreRejected() {
        assertThat(mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(PRIYA, PASSWORD))).hasStatus(403);
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() {
        var wrongPassword = login(new MockHttpSession(), PRIYA, "nope");
        var unknownEmail = login(new MockHttpSession(), "nobody@acme.example", "nope");

        assertThat(wrongPassword).hasStatus(401);
        assertThat(unknownEmail).hasStatus(401);
        assertThat(wrongPassword.getResponse().getContentAsByteArray())
                .isEqualTo(unknownEmail.getResponse().getContentAsByteArray());
    }

    @Test
    void locksAnAccountAfterRepeatedFailures() {
        String alex = "alex.morgan@acme.example";
        for (int i = 0; i < 5; i++) {
            login(new MockHttpSession(), alex, "guess-" + i);
        }

        var locked = login(new MockHttpSession(), alex, PASSWORD);

        assertThat(locked).hasStatus(429).hasHeader("Retry-After", "300");
        assertThat(locked).bodyJson().extractingPath("$.detail").asString().contains("Too many failed sign-in attempts");
    }

    @Test
    void recordsWhoChangedSalariesAndProfiles() {
        MockHttpSession session = signIn(PRIYA);
        Cookie token = xsrfCookie();
        String created = mvc.post().uri("/api/employees").session(session).cookie(token)
                .header("X-XSRF-TOKEN", token.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName":"Audit","lastName":"Trail","email":"audit@auth.test","countryCode":"US","departmentId":1,
                         "jobTitle":"Engineer","level":"L2","hireDate":"2025-01-01","salaryAmount":"100000"}""")
                .exchange().getResponse().getHeader("Location");

        var history = mvc.get().uri(created + "/salary-history").session(session);
        assertThat(history).hasStatusOk().bodyJson().extractingPath("$[0].recordedBy").isEqualTo("Priya Sharma");
        assertThat(mvc.get().uri(created).session(session)).bodyJson().extractingPath("$.updatedBy").isEqualTo("Priya Sharma");
    }

    private MockHttpSession signIn(String email) {
        MockHttpSession session = new MockHttpSession();
        assertThat(login(session, email, PASSWORD)).hasStatusOk();
        return session;
    }

    /** What the SPA does on start-up: ask for the session, which issues the CSRF cookie. */
    private Cookie xsrfCookie() {
        Cookie cookie = mvc.get().uri("/api/auth/session").exchange().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).as("XSRF-TOKEN cookie").isNotNull();
        return cookie;
    }

    private MvcTestResult login(MockHttpSession session, String email, String password) {
        Cookie token = xsrfCookie();
        return mvc.post().uri("/api/auth/login").session(session).cookie(token).header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(credentials(email, password)).exchange();
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }
}
