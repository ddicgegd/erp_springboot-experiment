package com.ddicg.erp.modules.subscription.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Google Play Real-Time Developer Notifications (RTDN) SubscriptionNotificationType
 * Ref: https://developer.android.com/google/play/billing/rtdn-reference
 */
@Getter
@RequiredArgsConstructor
public enum GoogleNotificationType {
    SUBSCRIPTION_RECOVERED(1, SubscriptionState.ACTIVE, "Khôi phục thành công từ trạng thái tạm ngưng"),
    SUBSCRIPTION_RENEWED(2, SubscriptionState.ACTIVE, "Gia hạn subscription thành công"),
    SUBSCRIPTION_CANCELED(3, SubscriptionState.CANCELLED, "Người dùng hủy tự động gia hạn"),
    SUBSCRIPTION_PURCHASED(4, SubscriptionState.ACTIVE, "Mua gói thành công"),
    SUBSCRIPTION_ON_HOLD(5, SubscriptionState.ON_HOLD, "Tài khoản bị giữ do lỗi thanh toán"),
    SUBSCRIPTION_IN_GRACE_PERIOD(6, SubscriptionState.IN_GRACE_PERIOD, "Rơi vào giai đoạn ân hạn thanh toán"),
    SUBSCRIPTION_RESTARTED(7, SubscriptionState.ACTIVE, "Kích hoạt lại gói thành công"),
    SUBSCRIPTION_PRICE_CHANGE_CONFIRMED(8, SubscriptionState.ACTIVE, "Xác nhận thay đổi giá"),
    SUBSCRIPTION_DEFERRED(9, SubscriptionState.ACTIVE, "Gia hạn thêm thời gian miễn phí"),
    SUBSCRIPTION_PAUSED(10, SubscriptionState.PAUSED, "Tạm dừng gói"),
    SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED(11, SubscriptionState.PAUSED, "Thay đổi lịch tạm dừng"),
    SUBSCRIPTION_REVOKED(12, SubscriptionState.EXPIRED, "Thu hồi quyền truy cập gói"),
    SUBSCRIPTION_EXPIRED(13, SubscriptionState.EXPIRED, "Gói hết hạn hoàn toàn");

    private final int value;
    private final SubscriptionState mappedState;
    private final String description;

    private static final Map<Integer, GoogleNotificationType> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toMap(GoogleNotificationType::getValue, Function.identity()));

    public static GoogleNotificationType fromValue(int value) {
        return LOOKUP.get(value);
    }
}
