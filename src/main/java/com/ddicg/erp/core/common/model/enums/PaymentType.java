package com.ddicg.erp.core.common.model.enums;

public enum PaymentType {
    ONLINE("Thanh toán trực tuyến"),
    OFFLINE("Thanh toán tại cửa hàng (COD / Cash)"),
    POSTPAID("Thanh toán sau (Ví dụ: Fineract)");

    private final String description;

    PaymentType(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
