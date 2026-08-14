package com.ddicg.erp.core.config;

import com.ddicg.erp.core.common.model.embedded.DeviceInfo;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PackageMigrationClassLoaderTest {

    @Test
    void testRemapLegacyDeviceInfoPackage() throws ClassNotFoundException {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("com.ddicg.erp.model.", "com.ddicg.erp.core.common.model.");
        mappings.put("com.anno.ERP_SpringBoot_Experiment.", "com.ddicg.erp.");

        PackageMigrationClassLoader classLoader = new PackageMigrationClassLoader(
                Thread.currentThread().getContextClassLoader(),
                mappings
        );

        Class<?> loadedClass = classLoader.loadClass("com.ddicg.erp.model.embedded.DeviceInfo");
        assertNotNull(loadedClass);
        assertEquals(DeviceInfo.class, loadedClass);
    }
}
