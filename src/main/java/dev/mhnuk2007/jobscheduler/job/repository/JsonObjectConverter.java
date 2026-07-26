package dev.mhnuk2007.jobscheduler.job.repository;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;


@Converter
public class JsonObjectConverter implements AttributeConverter<Object, String> {

    @Override
    public String convertToDatabaseColumn(Object attribute) {
        if (attribute == null) return null;
        try {
            return JsonSupport.MAPPER.writeValueAsString(attribute);
        } catch (JacksonException e) {
            throw new IllegalStateException("failed to serialize callback body", e);
        }
    }

    @Override
    public Object convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return JsonSupport.MAPPER.readValue(dbData, Object.class);
        } catch (JacksonException e) {
            return null;
        }
    }
}