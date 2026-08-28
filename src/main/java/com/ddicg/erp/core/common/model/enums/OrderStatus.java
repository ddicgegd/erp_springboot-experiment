package com.ddicg.erp.core.common.model.enums;

public enum OrderStatus {
    PENDING("Chờ xác nhận", "Chờ xác nhận"),
    WAITING_PAYMENT("Chờ thanh toán", "Chờ thanh toán"),
    CONFIRMED("Đã xác nhận", "Đã xác nhận"),
    PROCESSING("Đang xử lý", "Đang xử lý"),
    SHIPPING("Đang giao hàng", "Đang giao hàng"),
    READY_FOR_PICKUP("Chờ lấy hàng", "Chờ lấy hàng"),
    DELAYED("Giao hàng chậm", "Giao hàng chậm"),
    DELIVERED("Đã giao hàng", "Đã giao hàng"),
    COMPLETED("Hoàn thành", "Hoàn thành"),
    FAILED("Thanh toán thất bại", "Thanh toán thất bại"),
    CANCELLED("Đã hủy", "Đã hủy"),
    RETURNING("Hoàn trả hàng", "Hoàn trả hàng"),
    RETURNED("Đã trả hàng", "Đã trả hàng"),
    REFUNDED("Đã hoàn tiền", "Đã hoàn tiền");

    private final String displayName;
    private final String description;

    OrderStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
