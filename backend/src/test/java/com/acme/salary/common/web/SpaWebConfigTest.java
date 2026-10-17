package com.acme.salary.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses a stand-in index.html and bundle from src/test/resources/static. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaWebConfigTest {

    @Autowired MockMvcTester mvc;

    @Test
    void deepLinksServeTheAngularApp() {
        assertThat(mvc.get().uri("/employees/42?country=IN")).hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML)
                .hasHeader("Cache-Control", "no-cache")
                .bodyText().contains("<app-root>");
        // "/" is Spring Boot's welcome page: a server-side forward, which MockMvc records but doesn't execute.
        assertThat(mvc.get().uri("/")).hasStatusOk().hasForwardedUrl("index.html");
    }

    @Test
    void hashedBundlesAreCachedForAYear() {
        assertThat(mvc.get().uri("/main-ABCD1234.js")).hasStatusOk()
                .hasHeader("Cache-Control", "max-age=31536000, public, immutable");
    }

    @Test
    void fontsInMediaAreServedAndCachedForAYear() {
        // Regression: fonts 404'd in production because "/media/**" resolved against static/ instead of static/media/.
        assertThat(mvc.get().uri("/media/inter-latin-wght-normal-TEST1234.woff2")).hasStatusOk()
                .hasHeader("Cache-Control", "max-age=31536000, public, immutable");
    }

    @Test
    void missingFilesAreRealNotFounds() {
        assertThat(mvc.get().uri("/missing-file.js")).hasStatus(404);
        assertThat(mvc.get().uri("/maps/nope.json")).hasStatus(404);
    }

    @Test
    void unknownApiPathsReturnProblemJsonNotTheApp() {
        assertThat(mvc.get().uri("/api/nope").accept(MediaType.APPLICATION_JSON)).hasStatus(404)
                .bodyText().doesNotContain("<app-root>");
    }

    @Test
    void apiAndInfrastructureStillWin() {
        assertThat(mvc.get().uri("/api/meta")).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
    }
}
