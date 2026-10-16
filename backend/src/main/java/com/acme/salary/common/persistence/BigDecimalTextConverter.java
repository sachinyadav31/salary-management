package com.acme.salary.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

/** Keeps exact decimals as text; SQLite would otherwise store them as floating point. */
@Converter
public class BigDecimalTextConverter implements AttributeConverter<BigDecimal, String> {

    @Override
    public String convertToDatabaseColumn(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    @Override
    public BigDecimal convertToEntityAttribute(String text) {
        return text == null ? null : new BigDecimal(text);
    }
}
