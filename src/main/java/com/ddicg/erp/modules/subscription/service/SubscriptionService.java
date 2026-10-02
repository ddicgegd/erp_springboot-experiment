package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPlanRepository planRepository;

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getAllActivePlans() {
        return planRepository.findAll().stream()
                .filter(SubscriptionPlan::getIsActive)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<UserSubscriptionResponse> getLatestSubscriptionForUser(Long userId) {
        return userSubscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(sub -> {
                    SubscriptionPlan plan = planRepository.findById(sub.getPlanId()).orElse(null);
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
                });
    }
}
