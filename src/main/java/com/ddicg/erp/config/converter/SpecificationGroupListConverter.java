package com.ddicg.erp.config.converter;

import com.ddicg.erp.model.embedded.SpecificationGroup;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class SpecificationGroupListConverter implements AttributeConverter<List<SpecificationGroup>, String> {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<SpecificationGroup> attribute) {
        try {
            if (attribute == null) return "[]";
            return mapper.writeValueAsString(attribute);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public List<SpecificationGroup> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isEmpty()) return new ArrayList<>();
            return mapper.readValue(dbData, new TypeReference<List<SpecificationGroup>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
