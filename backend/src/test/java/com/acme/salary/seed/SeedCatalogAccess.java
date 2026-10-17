package com.acme.salary.seed;

import com.acme.salary.reference.JobLevel;

/** Test-only bridge to the package-private seed model. */
public final class SeedCatalogAccess {

    private SeedCatalogAccess() {
    }

    public static long bandMidpointMinor(String countryCode, JobLevel level) {
        return SeedCatalog.bandMidpointMinor(countryCode, level);
    }
}
