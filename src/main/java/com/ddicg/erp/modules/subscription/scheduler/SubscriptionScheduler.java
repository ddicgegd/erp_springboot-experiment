package com.ddicg.erp.modules.subscription.scheduler;

import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import com.ddicg.erp.modules.subscription.service.SubscriptionBillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler tự động hóa vòng đời Subscription:
 * 1. Quét các gói sắp hết hạn / đã đến hạn để gọi gia hạn định kỳ MIT.
 * 2. Quét các gói quá hạn để chuyển sang EXPIRED và hạ rank User về MEMBER nếu không còn subscription active.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private static final int GRACE_PERIOD_DAYS = 3;

    private static final List<SubscriptionState> RENEWABLE_STATES = List.of(
            SubscriptionState.ACTIVE,
            SubscriptionState.TRIALING,
            SubscriptionState.IN_GRACE_PERIOD
    );

    private static final List<SubscriptionState> ACTIVE_STATES = List.of(
            SubscriptionState.ACTIVE,
            SubscriptionState.TRIALING,
            SubscriptionState.IN_GRACE_PERIOD,
            SubscriptionState.CANCELLED
    );

    private final SubscriptionBillingService subscriptionBillingService;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserRepository userRepository;

    /**
     * Định kỳ quét các subscription đã đến hạn cần gia hạn tự động qua MIT Charge
     */
    @Scheduled(cron = "${app.subscription.renewal-cron:0 0/15 * * * *}")
    public void scanAndRenewSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        log.info("⏰ [SubscriptionScheduler] Bắt đầu quét các subscription đến hạn gia hạn lúc {}", now);

        List<UserSubscription> dueSubs = userSubscriptionRepository
                .findByAutoRenewingTrueAndSubscriptionStateInAndExpiryTimeLessThanEqual(RENEWABLE_STATES, now);

        if (dueSubs.isEmpty()) {
            log.info("ℹ️ Không có subscription nào cần gia hạn tại thời điểm này.");
            return;
        }

        log.info("🔍 Tìm thấy {} subscription đến hạn cần gia hạn định kỳ", dueSubs.size());

        for (UserSubscription sub : dueSubs) {
            try {
                log.info("🔄 Đang xử lý gia hạn cho Subscription ID [{}] của User [{}]", sub.getId(), sub.getUserId());
                boolean success = subscriptionBillingService.processRecurringRenewal(sub.getId());
                if (success) {
                    log.info("✅ Gia hạn thành công Subscription ID [{}] cho User [{}]", sub.getId(), sub.getUserId());
                } else {
                    log.warn("⚠️ Gia hạn thất bại cho Subscription ID [{}] (chuyển sang IN_GRACE_PERIOD)", sub.getId());
                }
            } catch (Exception e) {
                log.error("❌ Lỗi khi gia hạn Subscription ID [{}]: {}", sub.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Định kỳ quét các subscription quá hạn để chuyển sang EXPIRED và hạ rank User nếu cần
     */
    @Scheduled(cron = "${app.subscription.expiry-cron:0 0/30 * * * *}")
    @Transactional
    public void scanAndExpireOverdueSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        log.info("⏰ [SubscriptionScheduler] Bắt đầu quét các subscription quá hạn lúc {}", now);

        List<UserSubscription> overdueCandidates = userSubscriptionRepository
                .findBySubscriptionStateNotAndExpiryTimeLessThanEqual(SubscriptionState.EXPIRED, now);

        if (overdueCandidates.isEmpty()) {
            log.info("ℹ️ Không có subscription quá hạn nào cần xử lý.");
            return;
        }

        log.info("🔍 Tìm thấy {} subscription ứng viên quá hạn cần kiểm tra", overdueCandidates.size());

        LocalDateTime graceDeadline = now.minusDays(GRACE_PERIOD_DAYS);

        for (UserSubscription sub : overdueCandidates) {
            boolean shouldExpire = false;

            if (Boolean.FALSE.equals(sub.getAutoRenewing())) {
                // Không tự động gia hạn và đã quá hạn
                shouldExpire = true;
            } else if (sub.getSubscriptionState() == SubscriptionState.IN_GRACE_PERIOD
                    || sub.getSubscriptionState() == SubscriptionState.ACTIVE
                    || sub.getSubscriptionState() == SubscriptionState.TRIALING) {
                // Đang trong thời gian ân hạn hoặc còn treo active/trialing nhưng đã quá hạn ân hạn (3 ngày)
                if (sub.getExpiryTime() != null && sub.getExpiryTime().isBefore(graceDeadline)) {
                    shouldExpire = true;
                }
            } else if (sub.getSubscriptionState() == SubscriptionState.CANCELLED
                    || sub.getSubscriptionState() == SubscriptionState.ON_HOLD
                    || sub.getSubscriptionState() == SubscriptionState.PAUSED) {
                // Gói đã hủy hoặc tạm dừng và đã hết hạn
                shouldExpire = true;
            }

            if (shouldExpire) {
                log.info("⛔ Chuyển trạng thái Subscription ID [{}] sang EXPIRED (User ID: {})", sub.getId(), sub.getUserId());
                sub.setSubscriptionState(SubscriptionState.EXPIRED);
                userSubscriptionRepository.save(sub);

                // Kiểm tra hạ rank nếu user không còn subscription active nào
                checkAndDowngradeUserRank(sub.getUserId());
            }
        }
    }

    /**
     * Kiểm tra và hạ rank User về MEMBER nếu không còn subscription nào đang active
     */
    public void checkAndDowngradeUserRank(Long userId) {
        if (userId == null) {
            return;
        }

        boolean hasActive = userSubscriptionRepository
                .existsByUserIdAndSubscriptionStateInAndExpiryTimeAfter(userId, ACTIVE_STATES, LocalDateTime.now());

        if (!hasActive) {
            userRepository.findById(userId).ifPresent(user -> {
                if (user.getUserRank() != UserRank.MEMBER) {
                    log.info("🔻 [DOWNGRADE_RANK] Hạ rank User [{}] từ [{}] về MEMBER vì không còn Subscription active",
                            userId, user.getUserRank());
                    user.setRank(UserRank.MEMBER);
                    userRepository.save(user);
                }
            });
        }
    }
}
