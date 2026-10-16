package com.acme.salary.payband;

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
class PayBandControllerTest {

    @Autowired MockMvcTester mvc;

    @Test
    void listsAllBandsWithRangesInLocalCurrency() {
        var json = assertThat(mvc.get().uri("/api/pay-bands")).hasStatusOk().bodyJson();
        json.extractingPath("$.length()").isEqualTo(48);
        json.extractingPath("$[?(@.countryCode == 'US' && @.level == 'L3')].band.mid.amount")
                .asArray().containsExactly("119000.00");
        json.extractingPath("$[?(@.countryCode == 'JP' && @.level == 'L1')].band.min.amount")
                .asArray().containsExactly("3600000");
    }
}
