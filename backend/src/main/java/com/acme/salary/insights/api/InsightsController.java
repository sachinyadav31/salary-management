package com.acme.salary.insights.api;

import com.acme.salary.insights.InsightsCalculator;
import com.acme.salary.insights.InsightsModels.CountryInsight;
import com.acme.salary.insights.InsightsModels.Distribution;
import com.acme.salary.insights.InsightsModels.Envelope;
import com.acme.salary.insights.InsightsModels.Summary;
import com.acme.salary.insights.InsightsModels.TitleInsight;
import com.acme.salary.insights.WorkforceSnapshot;
import com.acme.salary.insights.WorkforceSnapshotCache;
import com.acme.salary.reference.CountryRepository;
import com.acme.salary.reference.DepartmentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** "How does the org pay people?" Every response is computed from the cached workforce snapshot. */
@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final WorkforceSnapshotCache snapshots;
    private final CountryRepository countries;
    private final DepartmentRepository departments;

    public InsightsController(WorkforceSnapshotCache snapshots, CountryRepository countries,
                              DepartmentRepository departments) {
        this.snapshots = snapshots;
        this.countries = countries;
        this.departments = departments;
    }

    @GetMapping("/summary")
    public Summary summary() {
        return InsightsCalculator.summary(snapshots.get());
    }

    @GetMapping("/by-country")
    public Envelope<List<CountryInsight>> byCountry() {
        WorkforceSnapshot snapshot = snapshots.get();
        return envelope(snapshot, InsightsCalculator.byCountry(snapshot));
    }

    @GetMapping("/by-title")
    public Envelope<List<TitleInsight>> byTitle(@RequestParam String country) {
        requireCountry(country);
        WorkforceSnapshot snapshot = snapshots.get();
        return envelope(snapshot, InsightsCalculator.byTitle(snapshot, country));
    }

    @GetMapping("/distribution")
    public Distribution distribution(@RequestParam(required = false) String country,
                                     @RequestParam(name = "department", required = false) Long departmentId) {
        if (country != null) {
            requireCountry(country);
        }
        if (departmentId != null && !departments.existsById(departmentId)) {
            throw new IllegalArgumentException("Unknown department: " + departmentId);
        }
        return InsightsCalculator.distribution(snapshots.get(), country, departmentId);
    }

    private void requireCountry(String country) {
        if (!countries.existsById(country)) {
            throw new IllegalArgumentException("Unknown country: " + country);
        }
    }

    private static <T> Envelope<T> envelope(WorkforceSnapshot snapshot, T data) {
        return new Envelope<>(snapshot.asOf(), snapshot.fxRatesAsOf(), data);
    }
}
