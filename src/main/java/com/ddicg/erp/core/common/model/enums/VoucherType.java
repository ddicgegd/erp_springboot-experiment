package com.ddicg.erp.core.common.model.enums;

import lombok.Getter;

@Getter
public enum VoucherType {
    SHIPPING("Giảm phí vận chuyển toàn đơn"),
    PRODUCT("Giảm giá sản phẩm theo Attributes");

    private final String description;

    VoucherType(String description) {
        this.description = description;
    }
}
