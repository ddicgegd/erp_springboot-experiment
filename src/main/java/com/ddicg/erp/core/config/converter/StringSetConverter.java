package com.ddicg.erp.core.config.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashSet;
import java.util.Set;

@Converter
public class StringSetConverter implements AttributeConverter<Set<String>, String> {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Set<String> attribute) {
        try {
            if (attribute == null) return "[]";
            return mapper.writeValueAsString(attribute);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public Set<String> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isEmpty()) return new HashSet<>();
            return mapper.readValue(dbData, new TypeReference<Set<String>>() {});
        } catch (Exception e) {
            return new HashSet<>();
        }
    }
}
