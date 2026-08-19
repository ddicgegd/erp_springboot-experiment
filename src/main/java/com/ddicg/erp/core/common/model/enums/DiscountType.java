package com.ddicg.erp.core.common.model.enums;

import lombok.Getter;

@Getter
public enum DiscountType {
    PERCENTAGE("Giảm theo phần trăm (%)"),
    FIXED_AMOUNT("Giảm số tiền cố định (VND)");

    private final String description;

    DiscountType(String description) {
        this.description = description;
    }
}
