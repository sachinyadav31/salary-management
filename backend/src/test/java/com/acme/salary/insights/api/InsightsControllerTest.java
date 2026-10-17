package com.acme.salary.insights.api;

import com.acme.salary.insights.WorkforceMember;
import com.acme.salary.insights.WorkforceSnapshot;
import com.acme.salary.insights.WorkforceSnapshotCache;
import com.acme.salary.payband.BandPosition;
import com.acme.salary.reference.JobLevel;
import com.acme.salary.reference.ReferenceDataService;
import org.junit.jupiter.api.BeforeEach;
import com.acme.salary.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@WebMvcTest(InsightsController.class)
@WithMockUser(roles = "HR")
@Import(SecurityConfig.class)
class InsightsControllerTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean WorkforceSnapshotCache cache;
    @MockitoBean ReferenceDataService referenceData;

    @BeforeEach
    void snapshot() {
        when(cache.get()).thenReturn(new WorkforceSnapshot(List.of(
                new WorkforceMember("IN", "India", "INR", 1, "Engineering", "Software Engineer", JobLevel.L2,
                        150_000_000, 1_800_000, 9_600L, BandPosition.WITHIN),
                new WorkforceMember("IN", "India", "INR", 1, "Engineering", "Software Engineer", JobLevel.L2,
                        90_000_000, 1_080_000, 7_700L, BandPosition.BELOW)),
                Instant.parse("2026-10-17T08:00:00Z"), "2026-10-01"));
        doThrow(new IllegalArgumentException("Unknown country: XX")).when(referenceData).requireCountry("XX");
        doThrow(new IllegalArgumentException("Unknown department: 99")).when(referenceData).requireDepartment(99L);
    }

    @Test
    void summaryCarriesAsOfDatesAndUsdTotals() {
        var json = assertThat(mvc.get().uri("/api/insights/summary")).hasStatusOk().bodyJson();
        json.extractingPath("$.asOf").isEqualTo("2026-10-17T08:00:00Z");
        json.extractingPath("$.fxRatesAsOf").isEqualTo("2026-10-01");
        json.extractingPath("$.totalPayroll.amount").isEqualTo("28800.00");
        json.extractingPath("$.bands.below").isEqualTo(1);
    }

    @Test
    void byCountryIsWrappedInAnEnvelope() {
        var json = assertThat(mvc.get().uri("/api/insights/by-country")).hasStatusOk().bodyJson();
        json.extractingPath("$.asOf").isEqualTo("2026-10-17T08:00:00Z");
        json.extractingPath("$.data[0].countryCode").isEqualTo("IN");
        json.extractingPath("$.data[0].local.median.amount").isEqualTo("1200000.00");
    }

    @Test
    void byTitleRequiresAKnownCountry() {
        assertThat(mvc.get().uri("/api/insights/by-title?country=IN")).hasStatusOk()
                .bodyJson().extractingPath("$.data[0].spread").isEqualTo(1.67);
        assertThat(mvc.get().uri("/api/insights/by-title")).hasStatus(400);
        assertThat(mvc.get().uri("/api/insights/by-title?country=XX")).hasStatus(400);
    }

    @Test
    void distributionValidatesFilters() {
        assertThat(mvc.get().uri("/api/insights/distribution?country=IN")).hasStatusOk()
                .bodyJson().extractingPath("$.currency").isEqualTo("INR");
        assertThat(mvc.get().uri("/api/insights/distribution?department=99")).hasStatus(400);
    }

    @Test
    void outliersReturnRankedCardsWithDirectoryFilters() {
        // 2 people is below the minimum group size, so the snapshot from setUp yields no cards
        var json = assertThat(mvc.get().uri("/api/insights/outliers")).hasStatusOk().bodyJson();
        json.extractingPath("$.data").asArray().isEmpty();
        assertThat(mvc.get().uri("/api/insights/outliers?limit=0")).hasStatus(400);
        assertThat(mvc.get().uri("/api/insights/outliers?limit=21")).hasStatus(400);
    }
}
