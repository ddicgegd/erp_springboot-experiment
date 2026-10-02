package com.ddicg.erp.modules.subscription.model;

import com.ddicg.erp.modules.subscription.model.enums.GoogleNotificationType;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionModelTest {

    @Test
    @DisplayName("Test SubscriptionPlan creation and defaults")
    void testSubscriptionPlan() {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .planCode("VIP_3M")
                .googleProductId("sub_vip")
                .basePlanId("plan-3-months")
                .targetRank("VIP")
                .billingPeriodMonths(3)
                .freeTrialDays(30)
                .priceAmount(BigDecimal.valueOf(299000))
                .currency("VND")
                .isActive(true)
                .build();

        assertEquals("VIP_3M", plan.getPlanCode());
        assertEquals(3, plan.getBillingPeriodMonths());
        assertEquals(30, plan.getFreeTrialDays());
        assertTrue(plan.getIsActive());
    }

    @Test
    @DisplayName("Test UserSubscription state and trial transitions")
    void testUserSubscription() {
        LocalDateTime now = LocalDateTime.now();
        UserSubscription sub = UserSubscription.builder()
                .userId(100L)
                .purchaseToken("sample_token_xyz")
                .orderId("GPA.1234-5678")
                .subscriptionState(SubscriptionState.TRIALING)
                .isTrial(true)
                .startTime(now)
                .expiryTime(now.plusDays(30))
                .autoRenewing(true)
                .build();

        assertEquals(SubscriptionState.TRIALING, sub.getSubscriptionState());
        assertTrue(sub.getIsTrial());
        assertTrue(sub.getAutoRenewing());
    }

    @Test
    @DisplayName("Test GoogleNotificationType mapping")
    void testGoogleNotificationType() {
        assertEquals(SubscriptionState.ACTIVE, GoogleNotificationType.SUBSCRIPTION_RENEWED.getMappedState());
        assertEquals(SubscriptionState.IN_GRACE_PERIOD, GoogleNotificationType.SUBSCRIPTION_IN_GRACE_PERIOD.getMappedState());
        assertEquals(SubscriptionState.ON_HOLD, GoogleNotificationType.SUBSCRIPTION_ON_HOLD.getMappedState());
        assertEquals(SubscriptionState.EXPIRED, GoogleNotificationType.SUBSCRIPTION_EXPIRED.getMappedState());
    }
}
