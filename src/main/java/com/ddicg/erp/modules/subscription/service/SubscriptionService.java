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

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getActivePlans() {
        return planRepository.findByIsActiveTrue();
    }

    @Transactional(readOnly = true)
    public UserSubscriptionResponse getMySubscription(Long userId) {
        UserSubscription sub = userSubscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElse(null);

        if (sub == null) {
            return null;
        }

        SubscriptionPlan plan = planRepository.findById(sub.getPlanId()).orElse(null);

        return UserSubscriptionResponse.builder()
                .id(sub.getId())
                .userId(sub.getUserId())
                .planId(sub.getPlanId())
                .planCode(plan != null ? plan.getPlanCode() : null)
                .planName(plan != null ? plan.getName() : null)
                .targetRank(plan != null ? plan.getTargetRank() : null)
                .billingPeriodMonths(plan != null ? plan.getBillingPeriodMonths() : null)
                .subscriptionState(sub.getSubscriptionState())
                .isTrial(sub.getIsTrial())
                .startTime(sub.getStartTime())
                .expiryTime(sub.getExpiryTime())
                .autoRenewing(sub.getAutoRenewing())
                .paymentProfileId(sub.getPaymentProfileId())
                .build();
    }
}
