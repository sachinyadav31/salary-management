package com.acme.salary.reference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetaControllerTest {

    @Autowired MockMvcTester mvc;

    @Test
    void exposesReferenceDataForFilters() {
        var json = assertThat(mvc.get().uri("/api/meta")).hasStatusOk().bodyJson();
        json.extractingPath("$.countries.length()").isEqualTo(8);
        json.extractingPath("$.countries[?(@.code == 'JP')].currency").asArray().containsExactly("JPY");
        json.extractingPath("$.departments.length()").isEqualTo(8);
        json.extractingPath("$.levels.length()").isEqualTo(6);
        json.extractingPath("$.reportingCurrency").isEqualTo("USD");
        json.extractingPath("$.fxRatesAsOf").isEqualTo("2026-10-01");
    }
}
