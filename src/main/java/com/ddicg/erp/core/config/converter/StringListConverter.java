package com.ddicg.erp.core.config.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        try {
            if (attribute == null || attribute.isEmpty()) {
                return "[]";
            }
            return mapper.writeValueAsString(attribute);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.trim().isEmpty() || dbData.equals("[]")) {
                return new ArrayList<>();
            }
            if (dbData.startsWith("[") && dbData.endsWith("]")) {
                return mapper.readValue(dbData, new TypeReference<List<String>>() {});
            }
            // Fallback nếu dữ liệu cũ dạng phân tách dấu phẩy hoặc chuỗi đơn
            String[] parts = dbData.split(",");
            List<String> result = new ArrayList<>();
            for (String p : parts) {
                if (!p.trim().isEmpty()) {
                    result.add(p.trim());
                }
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
