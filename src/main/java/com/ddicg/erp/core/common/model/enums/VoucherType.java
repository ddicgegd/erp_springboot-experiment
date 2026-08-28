package com.ddicg.erp.core.common.model.enums;

import lombok.Getter;

@Getter
public enum VoucherType {
    GLOBAL_ORDER("Giảm giá tiền hàng toàn bộ đơn hàng"),
    PRODUCT_ITEM("Giảm giá tiền hàng theo từng sản phẩm (Attributes)"),
    SHIPPING("Giảm phí vận chuyển đơn hàng");

    private final String description;

    VoucherType(String description) {
        this.description = description;
    }
}

