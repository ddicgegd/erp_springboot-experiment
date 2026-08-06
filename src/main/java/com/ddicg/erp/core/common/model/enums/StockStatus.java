package com.ddicg.erp.core.common.model.enums;

public enum StockStatus {
    AVAILABLE("Còn hàng"),
    UNAVAILABLE("Hết hàng"),
    COMING_SOON("Hàng sắp về"),
    NOT_ACTIVE("Chưa hoạt động");

    private final String value;

    StockStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
