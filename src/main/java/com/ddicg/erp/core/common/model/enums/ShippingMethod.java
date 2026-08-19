package com.ddicg.erp.core.common.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ShippingMethod {
    DELIVERY("Giao hàng tận nơi"),
    PICKUP("Đến lấy tại kho / Cửa hàng");

    private final String description;

    public static ShippingMethod fromString(String value) {
        if (value == null || value.isBlank()) {
            return DELIVERY;
        }
        for (ShippingMethod method : values()) {
            if (method.name().equalsIgnoreCase(value.trim()) || method.getDescription().equalsIgnoreCase(value.trim())) {
                return method;
            }
        }
        return DELIVERY;
    }
}
