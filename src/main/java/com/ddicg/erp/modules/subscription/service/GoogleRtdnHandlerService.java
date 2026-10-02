package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.subscription.model.SubscriptionAuditLog;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.GoogleNotificationType;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.SubscriptionAuditLogRepository;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleRtdnHandlerService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void processNotification(String purchaseToken, int notificationTypeValue, String rawPayload) {
        log.info("Processing RTDN notification: type={}, token={}", notificationTypeValue, purchaseToken);

        GoogleNotificationType notificationType = GoogleNotificationType.fromValue(notificationTypeValue);
        if (notificationType == null) {
            log.warn("Unknown notification type: {}", notificationTypeValue);
            return;
        }

        Optional<UserSubscription> subOpt = userSubscriptionRepository.findByPurchaseToken(purchaseToken);
        if (subOpt.isEmpty()) {
            log.warn("Subscription not found for token: {}", purchaseToken);
            auditLogRepository.save(SubscriptionAuditLog.builder()
                    .purchaseToken(purchaseToken)
                    .notificationType(notificationTypeValue)
                    .rawPayload(rawPayload)
                    .build());
            return;
        }

        UserSubscription sub = subOpt.get();
        SubscriptionPlan plan = planRepository.findById(sub.getPlanId()).orElse(null);

        auditLogRepository.save(SubscriptionAuditLog.builder()
                .subscriptionId(sub.getId())
                .purchaseToken(purchaseToken)
                .notificationType(notificationTypeValue)
                .rawPayload(rawPayload)
                .build());

        handleStateTransition(sub, plan, notificationType);
        userSubscriptionRepository.save(sub);
    }

    private void handleStateTransition(UserSubscription sub, SubscriptionPlan plan, GoogleNotificationType notificationType) {
        switch (notificationType) {
            case SUBSCRIPTION_RENEWED -> {
                sub.setSubscriptionState(SubscriptionState.ACTIVE);
                sub.setIsTrial(false);
                sub.setAutoRenewing(true);
                int monthsToAdd = (plan != null && plan.getBillingPeriodMonths() != null) ? plan.getBillingPeriodMonths() : 3;
                sub.setExpiryTime(sub.getExpiryTime().plusMonths(monthsToAdd));
                if (plan != null) {
                    setUserRank(sub.getUserId(), plan.getTargetRank());
                }
            }
            case SUBSCRIPTION_CANCELED -> {
                sub.setAutoRenewing(false);
            }
            case SUBSCRIPTION_IN_GRACE_PERIOD -> {
                sub.setSubscriptionState(SubscriptionState.IN_GRACE_PERIOD);
                log.info("User {} subscription entered grace period", sub.getUserId());
            }
            case SUBSCRIPTION_ON_HOLD -> {
                sub.setSubscriptionState(SubscriptionState.ON_HOLD);
                setUserRank(sub.getUserId(), UserRank.MEMBER.name());
            }
            case SUBSCRIPTION_RECOVERED, SUBSCRIPTION_RESTARTED -> {
                sub.setSubscriptionState(SubscriptionState.ACTIVE);
                sub.setAutoRenewing(true);
                if (plan != null) {
                    setUserRank(sub.getUserId(), plan.getTargetRank());
                }
            }
            case SUBSCRIPTION_EXPIRED, SUBSCRIPTION_REVOKED -> {
                sub.setSubscriptionState(SubscriptionState.EXPIRED);
                sub.setAutoRenewing(false);
                setUserRank(sub.getUserId(), UserRank.MEMBER.name());
            }
            default -> log.info("Unhandled notification type: {}", notificationType);
        }
    }

    private void setUserRank(Long userId, String rankName) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                UserRank rank = UserRank.valueOf(rankName.toUpperCase());
                user.setRank(rank);
                userRepository.save(user);
                log.info("Updated rank for user {} to {}", userId, rank);
            }
        } catch (Exception e) {
            log.error("Failed to update rank for user {}: {}", userId, e.getMessage());
        }
    }
}
