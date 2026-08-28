package com.ddicg.erp.core.common.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ShippingMethod {
    DELIVERY("Giao hàng tận nơi"),
    PICKUP("Nhận tại cửa hàng");

    private final String description;

    @JsonCreator
    public static ShippingMethod fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ShippingMethod method : values()) {
            if (method.name().equalsIgnoreCase(value.trim()) || method.getDescription().equalsIgnoreCase(value.trim())) {
                return method;
            }
        }
        String lower = value.toLowerCase();
        if (lower.contains("giao") || lower.contains("tận nơi") || lower.contains("tiết kiệm") || lower.contains("hỏa tốc") || lower.contains("nhanh") || lower.contains("delivery")) {
            return DELIVERY;
        }
        if (lower.contains("kho") || lower.contains("cửa hàng") || lower.contains("lấy") || lower.contains("pickup")) {
            return PICKUP;
        }
        return DELIVERY;
    }

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
