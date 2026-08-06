package com.ddicg.erp.core.common.model.enums;

public enum PaymentMethod {
    COD("Thanh toán khi nhận hàng"),
    VNPAY("Thanh toán qua VNPay"),
    MOMO("Thanh toán qua MoMo"),
    BANK_TRANSFER("Chuyển khoản ngân hàng"),
    CREDIT_CARD("Thẻ tín dụng"),
    PAYPAL("Thanh toán qua PayPal");

    private final String description;

    PaymentMethod(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
