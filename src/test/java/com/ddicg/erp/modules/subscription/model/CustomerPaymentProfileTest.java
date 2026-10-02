package com.ddicg.erp.modules.subscription.model;

import com.ddicg.erp.modules.payment.model.CustomerPaymentProfile;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CustomerPaymentProfileTest {

    @Test
    @DisplayName("Should create CustomerPaymentProfile and link to user")
    void testCustomerPaymentProfileCreation() {
        CustomerPaymentProfile profile = CustomerPaymentProfile.builder()
                .id("profile_12345")
                .userId(101L)
                .gateway("GOOGLE_PAY_INTERNAL")
                .multiUseToken("vault_tok_abcdef98765")
                .fpanLastFour("4242")
                .cardNetwork("VISA")
                .status("ACTIVE")
                .initialCitTransactionId("cit_txn_initial_001")
                .build();

        assertNotNull(profile);
        assertEquals(101L, profile.getUserId());
        assertEquals("vault_tok_abcdef98765", profile.getMultiUseToken());
        assertEquals("ACTIVE", profile.getStatus());
        assertEquals("4242", profile.getFpanLastFour());
    }

    @Test
    @DisplayName("UserSubscription should reference CustomerPaymentProfile id and plan")
    void testUserSubscriptionLinkingPaymentProfile() {
        UserSubscription sub = UserSubscription.builder()
                .id(1L)
                .userId(101L)
                .planId(2L)
                .paymentProfileId("profile_12345")
                .subscriptionState(SubscriptionState.TRIALING)
                .isTrial(true)
                .startTime(LocalDateTime.now())
                .expiryTime(LocalDateTime.now().plusDays(30))
                .autoRenewing(true)
                .build();

        assertNotNull(sub);
        assertEquals("profile_12345", sub.getPaymentProfileId());
        assertTrue(sub.getIsTrial());
        assertEquals(SubscriptionState.TRIALING, sub.getSubscriptionState());
    }
}
