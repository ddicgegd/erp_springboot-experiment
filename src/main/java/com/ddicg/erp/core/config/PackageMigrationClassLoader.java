package com.ddicg.erp.core.config;

import java.util.Map;

/**
 * ClassLoader thực hiện migration class name khi deserialize từ Redis.
 * <p>
 * Khi Redis data được serialize với package cũ (com.anno.ERP_SpringBoot_Experiment, com.ddicg.erp.model...),
 * Jackson sẽ gọi ClassLoader để tìm class theo tên đó. ClassLoader này tự động
 * remap tên cũ sang tên mới trước khi load.
 * </p>
 * Có thể bỏ class này sau khi Redis đã được flush sạch và không còn data cũ.
 */
public class PackageMigrationClassLoader extends ClassLoader {

    private final Map<String, String> mappings;

    public PackageMigrationClassLoader(ClassLoader parent, Map<String, String> mappings) {
        super(parent);
        this.mappings = mappings;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        // Thử load với tên gốc trước
        try {
            return super.loadClass(name);
        } catch (ClassNotFoundException e) {
            // Thử từng rule mapping
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                if (name.startsWith(entry.getKey())) {
                    String remapped = entry.getValue() + name.substring(entry.getKey().length());
                    try {
                        ClassLoader ctl = Thread.currentThread().getContextClassLoader();
                        if (ctl != null && ctl != this) {
                            try {
                                return ctl.loadClass(remapped);
                            } catch (ClassNotFoundException ignored) {}
                        }
                        return super.loadClass(remapped);
                    } catch (ClassNotFoundException ignored) {
                        // Thử mapping tiếp theo nếu có
                    }
                }
            }
            throw e;
        }
    }
}
