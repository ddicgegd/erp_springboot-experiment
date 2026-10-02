package com.ddicg.erp.modules.subscription.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubscriptionState {
    PENDING("Đang chờ xử lý xác thực"),
    TRIALING("Đang trong thời gian dùng thử miễn phí"),
    ACTIVE("Đang hoạt động và đã gia hạn thành công"),
    IN_GRACE_PERIOD("Đang trong thời gian ân hạn thanh toán"),
    ON_HOLD("Tạm ngưng do thanh toán không thành công"),
    PAUSED("Người dùng chủ động tạm dừng gói"),
    CANCELLED("Đã hủy tự động gia hạn"),
    EXPIRED("Gói đã hết hạn hoàn toàn");

    private final String description;
}
