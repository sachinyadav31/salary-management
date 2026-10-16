package com.acme.salary.reference;

import com.acme.salary.common.persistence.BigDecimalTextConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fx_rate")
public class FxRate {

    @Id
    private String currencyCode;

    @Convert(converter = BigDecimalTextConverter.class)
    private BigDecimal usdPerUnit;

    private LocalDate asOfDate;

    protected FxRate() {
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getUsdPerUnit() {
        return usdPerUnit;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }
}
