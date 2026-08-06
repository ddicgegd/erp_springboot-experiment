package com.ddicg.erp.core.config.converter;

import com.ddicg.erp.core.common.model.embedded.DeviceInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Converter
public class DeviceInfoListConverter implements AttributeConverter<List<DeviceInfo>, String> {

    private static final Logger logger = LoggerFactory.getLogger(DeviceInfoListConverter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<DeviceInfo> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            logger.error("Lỗi khi chuyển đổi List<DeviceInfo> sang chuỗi JSON: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể chuyển đổi List<DeviceInfo> thành chuỗi JSON", e);
        }
    }

    @Override
    public List<DeviceInfo> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) return new ArrayList<>();
        try {
            return objectMapper.readValue(dbData, new TypeReference<List<DeviceInfo>>() {});
        } catch (IOException e) {
            logger.error("Lỗi khi chuyển đổi chuỗi JSON thành List<DeviceInfo>: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể chuyển đổi chuỗi JSON thành List<DeviceInfo>", e);
        }
    }
}