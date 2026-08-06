package com.ddicg.erp.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    UNPAID("Chưa thanh toán"),
    PENDING("Đang xử lý"),
    COD("Thanh toán khi nhận hàng"),
    PAID("Đã thanh toán"),
    REFUND_FAILED("Xử lý thanh toán hoàn trả hàng thất bại"),
    REFUNDED("Đã hoàn tiền"),
    FAILED("Thanh toán thất bại"),
    CANCELLED("Đã hủy");

    private final String displayName;
}
