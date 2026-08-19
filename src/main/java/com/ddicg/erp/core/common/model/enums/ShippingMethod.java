package com.ddicg.erp.core.common.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ShippingMethod {
    DELIVERY("Giao hàng tận nơi"),
    PICKUP("Đến lấy tại kho / Cửa hàng");

    private final String description;

    @JsonCreator
    public static ShippingMethod fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Phương thức nhận hàng (shippingMethod) không được để trống. Chỉ chấp nhận 'DELIVERY' hoặc 'PICKUP'");
        }
        for (ShippingMethod method : values()) {
            if (method.name().equalsIgnoreCase(value.trim())) {
                return method;
            }
        }
        throw new IllegalArgumentException("Phương thức nhận hàng '" + value + "' không hợp lệ. Chỉ chấp nhận 'DELIVERY' hoặc 'PICKUP'");
    }

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
