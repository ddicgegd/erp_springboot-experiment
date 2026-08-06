package com.ddicg.erp.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CachingStatus {

    PENDING("Đang chờ hệ thống tạo"),
    PROCESSING("Đang tạo đề xuất"),
    GENERATED("Tạo xong và đã lưu vào Redis"),
    CACHED("Lấy từ Redis"),
    EXPIRED("Redis TTL hết"),
    INVALIDATED(" Bị xóa do user thay đổi hành vi"),
    FAILED("Tạo thất bại");

    private final String message;
}
