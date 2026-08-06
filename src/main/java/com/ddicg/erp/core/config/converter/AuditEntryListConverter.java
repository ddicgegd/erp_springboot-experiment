package com.ddicg.erp.core.config.converter;

import com.ddicg.erp.core.common.model.embedded.AuditEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class AuditEntryListConverter implements AttributeConverter<List<AuditEntry>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public String convertToDatabaseColumn(List<AuditEntry> attribute) {
        try {
            if (attribute == null) return "[]";
            return mapper.writeValueAsString(attribute);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public List<AuditEntry> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isEmpty()) return new ArrayList<>();
            return mapper.readValue(dbData, new TypeReference<List<AuditEntry>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
