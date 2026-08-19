package com.ddicg.erp.core.config.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StringListConverterTest {

    private StringListConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StringListConverter();
    }

    @Test
    @DisplayName("Convert List<String> to JSON String in DB")
    void testConvertToDatabaseColumn() {
        List<String> list = List.of("FREESHIP", "GIAM10");
        String json = converter.convertToDatabaseColumn(list);

        assertNotNull(json);
        assertTrue(json.contains("FREESHIP"));
        assertTrue(json.contains("GIAM10"));
    }

    @Test
    @DisplayName("Convert null or empty List to '[]'")
    void testConvertToDatabaseColumn_Empty() {
        assertEquals("[]", converter.convertToDatabaseColumn(null));
        assertEquals("[]", converter.convertToDatabaseColumn(List.of()));
    }

    @Test
    @DisplayName("Convert JSON String from DB to List<String>")
    void testConvertToEntityAttribute_Json() {
        String json = "[\"FREESHIP\",\"GIAM10\"]";
        List<String> result = converter.convertToEntityAttribute(json);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("FREESHIP", result.get(0));
        assertEquals("GIAM10", result.get(1));
    }

    @Test
    @DisplayName("Convert comma-separated fallback String to List<String>")
    void testConvertToEntityAttribute_CommaSeparated() {
        String data = "FREESHIP, GIAM10";
        List<String> result = converter.convertToEntityAttribute(data);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("FREESHIP", result.get(0));
        assertEquals("GIAM10", result.get(1));
    }
}
