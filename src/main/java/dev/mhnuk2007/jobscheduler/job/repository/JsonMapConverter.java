package dev.mhnuk2007.jobscheduler.job.repository;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

class JsonSupport {
    private JsonSupport() {
    }

    static final JsonMapper MAPPER = JsonMapper.builder().build();
}

@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, String>, String> {


    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if(attribute == null) return null;
        try{
            return JsonSupport.MAPPER.writeValueAsString(attribute);
        } catch (JacksonException e) {
            throw new IllegalStateException("failed to serialize callback headers", e);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if(dbData == null || dbData.isBlank()) return null;
        try {
            return JsonSupport.MAPPER.readValue(dbData, Map.class);
        } catch (JacksonException e) {
            return null;
        }
    }
}