package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.subscription.dto.request.GooglePurchaseVerifyRequest;
import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import com.ddicg.erp.modules.subscription.service.client.GooglePlaySubscriptionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GooglePlayVerificationService {

    private final GooglePlaySubscriptionClient subscriptionClient;
    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserSubscriptionResponse verifyAndActivate(Long userId, GooglePurchaseVerifyRequest request) {
        log.info("Verifying Google Play purchase token for userId: {}, planCode: {}", userId, request.getPlanCode());

        Optional<UserSubscription> existingOpt = userSubscriptionRepository.findByPurchaseToken(request.getPurchaseToken());
        if (existingOpt.isPresent()) {
            return mapToResponse(existingOpt.get(), null);
        }

        SubscriptionPlan plan = planRepository.findByPlanCode(request.getPlanCode())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy gói subscription: " + request.getPlanCode()));

        GooglePlaySubscriptionClient.SubscriptionPurchaseV2 purchase = subscriptionClient.getSubscriptionPurchase(request.getPurchaseToken());
        if (!"SUBSCRIPTION_STATE_ACTIVE".equalsIgnoreCase(purchase.getSubscriptionState())) {
            throw new IllegalStateException("Trạng thái subscription từ Google Play không hợp lệ: " + purchase.getSubscriptionState());
        }

        subscriptionClient.acknowledgeSubscription(request.getPurchaseToken());

        LocalDateTime startTime = LocalDateTime.ofInstant(purchase.getStartTime(), ZoneId.systemDefault());
        LocalDateTime expiryTime = LocalDateTime.ofInstant(purchase.getExpiryTime(), ZoneId.systemDefault());

        boolean isTrial = purchase.getLineItems() != null && purchase.getLineItems().stream()
                .anyMatch(item -> item.getOfferId() != null && item.getOfferId().toLowerCase().contains("trial"));

        UserSubscription subscription = UserSubscription.builder()
                .userId(userId)
                .planId(plan.getId())
                .purchaseToken(request.getPurchaseToken())
                .orderId(request.getOrderId())
                .subscriptionState(isTrial ? SubscriptionState.TRIALING : SubscriptionState.ACTIVE)
                .isTrial(isTrial)
                .startTime(startTime)
                .expiryTime(expiryTime)
                .autoRenewing(true)
                .build();

        subscription = userSubscriptionRepository.save(subscription);

        updateUserRank(userId, plan.getTargetRank());

        return mapToResponse(subscription, plan);
    }

    private void updateUserRank(Long userId, String targetRankStr) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                UserRank targetRank = UserRank.valueOf(targetRankStr.toUpperCase());
                user.setRank(targetRank);
                userRepository.save(user);
                log.info("Updated user {} rank to {}", userId, targetRank);
            }
        } catch (Exception e) {
            log.error("Failed to update rank for user {}: {}", userId, e.getMessage());
        }
    }

    private UserSubscriptionResponse mapToResponse(UserSubscription sub, SubscriptionPlan plan) {
        if (plan == null) {
            plan = planRepository.findById(sub.getPlanId()).orElse(null);
        }
        return UserSubscriptionResponse.builder()
                .id(sub.getId())
                .userId(sub.getUserId())
                .planCode(plan != null ? plan.getPlanCode() : null)
                .targetRank(plan != null ? plan.getTargetRank() : null)
                .billingPeriodMonths(plan != null ? plan.getBillingPeriodMonths() : null)
                .subscriptionState(sub.getSubscriptionState())
                .isTrial(sub.getIsTrial())
                .startTime(sub.getStartTime())
                .expiryTime(sub.getExpiryTime())
                .autoRenewing(sub.getAutoRenewing())
                .orderId(sub.getOrderId())
                .build();
    }
}
