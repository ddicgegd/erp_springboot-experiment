package com.ddicg.erp.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentType {

    PAYMENT_UPON_DELIVERY("Thanh toán khi nhận hàng"),

    MOMO("Thanh toán qua Momo"),
    BUY_NOW_PAY_LATER("Mua trước trả sau");
    private final String description;
}
