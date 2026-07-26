package dev.mhnuk2007.jobscheduler.job.repository;

import dev.mhnuk2007.jobscheduler.job.repository.JsonSupport;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;

import java.util.Map;

@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, String>, String> {

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if (attribute == null) return null;
        try {
            return JsonSupport.MAPPER.writeValueAsString(attribute);
        } catch (JacksonException e) {
            throw new IllegalStateException("failed to serialize callback headers", e);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return JsonSupport.MAPPER.readValue(dbData, Map.class);
        } catch (JacksonException e) {
            return null; // malformed stored value degrades to "missing headers", not a crash
        }
    }
}

