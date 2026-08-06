package com.ddicg.erp.config.converter;

import com.ddicg.erp.model.embedded.DeviceInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class StringToDeviceInfoConverter implements Converter<String, DeviceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(StringToDeviceInfoConverter.class);
    private final ObjectMapper objectMapper;

    @Override
    public DeviceInfo convert(String source) {
        if (source.isEmpty()) return null;
        try {
            return objectMapper.readValue(source, DeviceInfo.class);
        } catch (JsonProcessingException e) {
            logger.error("Lỗi khi chuyển đổi chuỗi JSON thành DeviceInfo: {}. Chuỗi đầu vào: {}", e.getMessage(), source, e);
            return null;
        }
    }
}
