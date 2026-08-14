package com.ddicg.erp.core.config.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CustomLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        text = text.trim();
        try {
            if (text.length() == 10) {
                // Parse "yyyy-MM-dd" to LocalDateTime at start of day
                return LocalDate.parse(text).atStartOfDay();
            }
            // Parse standard ISO "yyyy-MM-dd'T'HH:mm:ss"
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                // Parse format with space "yyyy-MM-dd HH:mm:ss"
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ex) {
                // If everything fails, let it throw the original parsing error
                throw new IOException("Cannot parse date: " + text, ex);
            }
        }
    }
}
