package com.ddicg.erp.core.common.model.enums;

public enum CachingStatus {
    PENDING("Chưa cache"),
    CACHED("Đã cache"),
    INVALIDATED("Cache hết hạn"),
    PROCESSING("Đang xử lý cache"),
    FAILED("Lỗi cache"),
    DISABLED("Tắt cache");

    private final String description;

    CachingStatus(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
