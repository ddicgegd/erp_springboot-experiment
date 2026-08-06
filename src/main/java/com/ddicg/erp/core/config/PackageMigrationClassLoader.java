package com.ddicg.erp.core.config;

/**
 * ClassLoader thực hiện migration class name khi deserialize từ Redis.
 * <p>
 * Khi Redis data được serialize với package cũ (com.anno.ERP_SpringBoot_Experiment),
 * Jackson sẽ gọi ClassLoader để tìm class theo tên đó. ClassLoader này tự động
 * remap tên cũ sang tên mới (com.ddicg.erp) trước khi load.
 * </p>
 * Có thể bỏ class này sau khi Redis đã được flush sạch và không còn data cũ.
 */
public class PackageMigrationClassLoader extends ClassLoader {

    private final String oldPrefix;
    private final String newPrefix;

    public PackageMigrationClassLoader(ClassLoader parent, String oldPrefix, String newPrefix) {
        super(parent);
        this.oldPrefix = oldPrefix;
        this.newPrefix = newPrefix;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        // Thử load với tên gốc trước
        try {
            return super.loadClass(name);
        } catch (ClassNotFoundException e) {
            // Nếu fail và tên khớp với package cũ, thử remap sang package mới
            if (name.startsWith(oldPrefix)) {
                String remapped = newPrefix + name.substring(oldPrefix.length());
                return super.loadClass(remapped);
            }
            throw e;
        }
    }
}
