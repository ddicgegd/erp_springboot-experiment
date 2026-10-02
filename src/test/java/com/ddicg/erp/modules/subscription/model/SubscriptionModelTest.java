package com.ddicg.erp.modules.subscription.model;

import com.ddicg.erp.modules.payment.model.CustomerPaymentProfile;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionModelTest {

    @Test
    @DisplayName("SubscriptionPlan should hold correct values")
    void testSubscriptionPlan() {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(1L)
                .planCode("GOLD_3M")
                .name("Gold 3M")
                .targetRank("GOLD")
                .billingPeriodMonths(3)
                .freeTrialDays(30)
                .priceAmount(BigDecimal.valueOf(299000))
                .currency("VND")
                .isActive(true)
                .build();

        assertEquals("GOLD_3M", plan.getPlanCode());
        assertEquals("GOLD", plan.getTargetRank());
        assertEquals(3, plan.getBillingPeriodMonths());
        assertEquals(30, plan.getFreeTrialDays());
        assertTrue(plan.getIsActive());
    }

    @Test
    @DisplayName("UserSubscription state transitions and payment profile link")
    void testUserSubscription() {
        LocalDateTime now = LocalDateTime.now();
        UserSubscription sub = UserSubscription.builder()
                .id(100L)
                .userId(1L)
                .planId(1L)
                .paymentProfileId("prof_123")
                .subscriptionState(SubscriptionState.TRIALING)
                .isTrial(true)
                .startTime(now)
                .expiryTime(now.plusDays(30))
                .autoRenewing(true)
                .build();

        assertTrue(sub.getIsTrial());
        assertEquals(SubscriptionState.TRIALING, sub.getSubscriptionState());
        assertEquals("prof_123", sub.getPaymentProfileId());

        sub.setSubscriptionState(SubscriptionState.ACTIVE);
        sub.setIsTrial(false);
        assertEquals(SubscriptionState.ACTIVE, sub.getSubscriptionState());
        assertFalse(sub.getIsTrial());
    }
}
