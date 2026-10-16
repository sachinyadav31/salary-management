package com.acme.salary.seed;

import com.acme.salary.employee.JobLevel;

import java.util.List;
import java.util.Map;

/**
 * Static, realistic-looking reference values used to generate demo data.
 * Salaries are annual base pay in local currency.
 */
final class SeedCatalog {

    record CountryProfile(String code, String currency, int weight, long l1BaseMajor, long roundToMajor) {
    }

    record DepartmentProfile(String name, int weight, double payFactor, List<String> titlesByLevel) {
    }

    static final List<CountryProfile> COUNTRIES = List.of(
            new CountryProfile("US", "USD", 25, 70_000, 100),
            new CountryProfile("IN", "INR", 30, 800_000, 1_000),
            new CountryProfile("GB", "GBP", 10, 35_000, 100),
            new CountryProfile("DE", "EUR", 8, 48_000, 100),
            new CountryProfile("CA", "CAD", 7, 65_000, 100),
            new CountryProfile("SG", "SGD", 5, 55_000, 100),
            new CountryProfile("AU", "AUD", 5, 75_000, 100),
            new CountryProfile("JP", "JPY", 10, 4_500_000, 10_000));

    static final List<DepartmentProfile> DEPARTMENTS = List.of(
            new DepartmentProfile("Engineering", 35, 1.10, List.of("Associate Software Engineer", "Software Engineer",
                    "Senior Software Engineer", "Staff Engineer", "Principal Engineer", "Director of Engineering")),
            new DepartmentProfile("Product", 6, 1.10, List.of("Associate Product Manager", "Product Manager",
                    "Senior Product Manager", "Group Product Manager", "Principal Product Manager", "Director of Product")),
            new DepartmentProfile("Design", 5, 1.00, List.of("Junior Designer", "Product Designer",
                    "Senior Product Designer", "Lead Designer", "Principal Designer", "Head of Design")),
            new DepartmentProfile("Sales", 18, 0.95, List.of("Sales Development Rep", "Account Executive",
                    "Senior Account Executive", "Sales Manager", "Regional Sales Director", "VP Sales")),
            new DepartmentProfile("Marketing", 8, 0.95, List.of("Marketing Coordinator", "Marketing Specialist",
                    "Senior Marketing Manager", "Marketing Lead", "Head of Growth", "Director of Marketing")),
            new DepartmentProfile("Finance", 7, 1.00, List.of("Finance Associate", "Financial Analyst",
                    "Senior Financial Analyst", "Finance Manager", "Head of FP&A", "Finance Director")),
            new DepartmentProfile("People", 6, 0.90, List.of("People Coordinator", "HR Generalist",
                    "Senior HR Business Partner", "People Manager", "Head of Talent", "People Director")),
            new DepartmentProfile("Operations", 15, 0.85, List.of("Operations Associate", "Operations Analyst",
                    "Senior Operations Analyst", "Operations Manager", "Head of Operations", "Director of Operations")));

    static final Map<JobLevel, Double> LEVEL_MULTIPLIER = Map.of(
            JobLevel.L1, 1.0, JobLevel.L2, 1.3, JobLevel.L3, 1.7,
            JobLevel.L4, 2.2, JobLevel.L5, 2.9, JobLevel.L6, 3.8);

    /** Share of employees per level, in percent (L1..L6). */
    static final int[] LEVEL_WEIGHTS = {20, 25, 25, 15, 10, 5};

    static final List<String> FIRST_NAMES = List.of(
            "Aarav", "Aditi", "Akira", "Alex", "Amelia", "Ananya", "Arjun", "Ben", "Charlotte", "Chen",
            "Daniel", "Diya", "Emma", "Ethan", "Fatima", "Felix", "Hana", "Hiro", "Isabella", "Ishaan",
            "James", "Jia", "Kavya", "Kenji", "Lena", "Liam", "Lucas", "Maya", "Mei", "Mia",
            "Noah", "Olivia", "Priya", "Rahul", "Riya", "Sakura", "Sofia", "Tom", "Wei", "Zara");

    static final List<String> LAST_NAMES = List.of(
            "Agarwal", "Brown", "Chen", "Clarke", "Das", "Fischer", "Garcia", "Gupta", "Hoffmann", "Ito",
            "Iyer", "Jones", "Kapoor", "Kim", "Kumar", "Lee", "Martin", "Mehta", "Miller", "Muller",
            "Nair", "Nakamura", "Ng", "Patel", "Reddy", "Rao", "Schmidt", "Sharma", "Singh", "Smith",
            "Suzuki", "Tan", "Taylor", "Thompson", "Wagner", "Walker", "Wang", "Wilson", "Wong", "Yadav");

    private SeedCatalog() {
    }

    static CountryProfile country(String code) {
        return COUNTRIES.stream().filter(c -> c.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown country " + code));
    }

    static String[] countryCodes() {
        return COUNTRIES.stream().map(CountryProfile::code).toArray(String[]::new);
    }

    static String currencyOf(String countryCode) {
        return country(countryCode).currency();
    }

    /** Midpoint of the band (before department factor and noise) in minor units. */
    static long bandMidpointMinor(String countryCode, JobLevel level) {
        CountryProfile c = country(countryCode);
        int digits = java.util.Currency.getInstance(c.currency()).getDefaultFractionDigits();
        return Math.round(c.l1BaseMajor() * LEVEL_MULTIPLIER.get(level) * Math.pow(10, digits));
    }
}
