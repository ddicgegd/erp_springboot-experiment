package com.ddicg.erp.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StockStatus {
    AVAILABLE("Còn hàng"),
    UNAVAILABLE("Hết hàng"),
    COMING_SOON("Hàng sắp về"),
    NOT_ACTIVE("Chưa hoạt động");

    private final String value;
}
