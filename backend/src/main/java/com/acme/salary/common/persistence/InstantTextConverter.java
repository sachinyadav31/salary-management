package com.acme.salary.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;

/** Stores timestamps as ISO-8601 UTC text. */
@Converter(autoApply = true)
public class InstantTextConverter implements AttributeConverter<Instant, String> {

    @Override
    public String convertToDatabaseColumn(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    @Override
    public Instant convertToEntityAttribute(String text) {
        return text == null ? null : Instant.parse(text);
    }
}
