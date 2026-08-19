package com.ddicg.erp.core.common.model.enums;

import lombok.Getter;

@Getter
public enum VoucherReservationStatus {
    AVAILABLE("Có sẵn"),
    RESERVED("Đang tạm giữ chờ thanh toán (TTL 3 phút)"),
    COMMITTED("Đã sử dụng thành công"),
    RELEASED("Đã giải phóng / Hoàn trả");

    private final String description;

    VoucherReservationStatus(String description) {
        this.description = description;
    }
}
