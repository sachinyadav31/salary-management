package com.acme.salary.reference;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates references to countries and departments for other features, without exposing repositories. */
@Service
@Transactional(readOnly = true)
public class ReferenceDataService {

    private final CountryRepository countries;
    private final DepartmentRepository departments;

    public ReferenceDataService(CountryRepository countries, DepartmentRepository departments) {
        this.countries = countries;
        this.departments = departments;
    }

    public void requireCountry(String code) {
        if (!countries.existsById(code)) {
            throw new IllegalArgumentException("Unknown country: " + code);
        }
    }

    public void requireDepartment(long id) {
        if (!departments.existsById(id)) {
            throw new IllegalArgumentException("Unknown department: " + id);
        }
    }
}
