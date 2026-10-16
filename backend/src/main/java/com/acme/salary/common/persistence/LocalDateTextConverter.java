package com.acme.salary.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

/** Stores dates as ISO-8601 text (yyyy-MM-dd) so they sort and compare correctly in SQLite. */
@Converter(autoApply = true)
public class LocalDateTextConverter implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate date) {
        return date == null ? null : date.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String text) {
        return text == null ? null : LocalDate.parse(text);
    }
}
