package com.ddicg.erp.core.config.converter;

import com.ddicg.erp.core.common.model.embedded.MediaItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class MediaItemListConverter implements AttributeConverter<List<MediaItem>, String> {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<MediaItem> attribute) {
        try {
            if (attribute == null) return "[]";
            return mapper.writeValueAsString(attribute);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public List<MediaItem> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isEmpty()) return new ArrayList<>();
            return mapper.readValue(dbData, new TypeReference<List<MediaItem>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
