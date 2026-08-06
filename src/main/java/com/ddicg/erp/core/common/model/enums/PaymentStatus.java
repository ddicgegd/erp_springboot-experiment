package com.ddicg.erp.core.common.model.enums;

public enum PaymentStatus {
    PENDING("Chờ thanh toán"),
    PROCESSING("Đang xử lý thanh toán"),
    PAID("Đã thanh toán"),
    FAILED("Thanh toán thất bại"),
    CANCELLED("Đã hủy thanh toán"),
    REFUNDED("Đã hoàn tiền"),
    EXPIRED("Hết hạn thanh toán"),
    PARTIALLY_REFUNDED("Hoàn tiền một phần");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
