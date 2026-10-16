package com.acme.salary.reference;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "country")
public class Country {

    @Id
    private String code;
    private String name;
    private String currencyCode;

    protected Country() {
    }

    public Country(String code, String name, String currencyCode) {
        this.code = code;
        this.name = name;
        this.currencyCode = currencyCode;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }
}
