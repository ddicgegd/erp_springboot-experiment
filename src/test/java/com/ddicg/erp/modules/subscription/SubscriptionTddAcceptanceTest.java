package com.ddicg.erp.modules.subscription;

import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.subscription.dto.request.GooglePurchaseVerifyRequest;
import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.SubscriptionAuditLogRepository;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import com.ddicg.erp.modules.subscription.service.GooglePlayVerificationService;
import com.ddicg.erp.modules.subscription.service.GoogleRtdnHandlerService;
import com.ddicg.erp.modules.subscription.service.client.GooglePlaySubscriptionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TDD End-to-End Acceptance Test Suite for Subscription Module.
 * Tests public seams: Initial Verification, Renewal, Grace Period, Expiration, and Idempotency.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionTddAcceptanceTest {

    @Mock
    private GooglePlaySubscriptionClient subscriptionClient;

    @Mock
    private SubscriptionPlanRepository planRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private SubscriptionAuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GooglePlayVerificationService verificationService;

    @InjectMocks
    private GoogleRtdnHandlerService rtdnHandlerService;

    private SubscriptionPlan plan6M;
    private SubscriptionPlan plan12M;
    private User user;

    @BeforeEach
    void setUp() {
        plan6M = SubscriptionPlan.builder()
                .id(2L)
                .planCode("GOLD_6M")
                .googleProductId("sub_gold")
                .basePlanId("plan-6-months")
                .targetRank("GOLD")
                .billingPeriodMonths(6)
                .freeTrialDays(30)
                .priceAmount(BigDecimal.valueOf(550000))
                .currency("VND")
                .isActive(true)
                .build();

        plan12M = SubscriptionPlan.builder()
                .id(3L)
                .planCode("PLATINUM_12M")
                .googleProductId("sub_platinum")
                .basePlanId("plan-12-months")
                .targetRank("PLATINUM")
                .billingPeriodMonths(12)
                .freeTrialDays(30)
                .priceAmount(BigDecimal.valueOf(990000))
                .currency("VND")
                .isActive(true)
                .build();

        user = User.builder()
                .id(88L)
                .email("subscriber@example.com")
                .rank(UserRank.MEMBER)
                .build();
    }

    @Test
    @DisplayName("Seam 1: Initial Purchase Verification with 1 Month Free Trial (Plan 6M)")
    void testSeam1_InitialPurchase_FreeTrial() {
        String token = "seam1_purchase_token";
        GooglePurchaseVerifyRequest request = GooglePurchaseVerifyRequest.builder()
                .purchaseToken(token)
                .planCode("GOLD_6M")
                .orderId("GPA.8888-9999")
                .build();

        Instant now = Instant.now();
        GooglePlaySubscriptionClient.SubscriptionPurchaseV2 purchaseV2 = GooglePlaySubscriptionClient.SubscriptionPurchaseV2.builder()
                .subscriptionState("SUBSCRIPTION_STATE_ACTIVE")
                .startTime(now)
                .expiryTime(now.plus(30, ChronoUnit.DAYS))
                .acknowledgementState(false)
                .lineItems(List.of(GooglePlaySubscriptionClient.LineItem.builder()
                        .offerId("free-trial-30d")
                        .productId("sub_gold")
                        .basePlanId("plan-6-months")
                        .build()))
                .build();

        when(userSubscriptionRepository.findByPurchaseToken(token)).thenReturn(Optional.empty());
        when(planRepository.findByPlanCode("GOLD_6M")).thenReturn(Optional.of(plan6M));
        when(subscriptionClient.getSubscriptionPurchase(token)).thenReturn(purchaseV2);
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(i -> {
            UserSubscription s = i.getArgument(0);
            s.setId(201L);
            return s;
        });
        when(userRepository.findById(88L)).thenReturn(Optional.of(user));

        UserSubscriptionResponse res = verificationService.verifyAndActivate(88L, request);

        assertNotNull(res);
        assertEquals(SubscriptionState.TRIALING, res.getSubscriptionState());
        assertTrue(res.getIsTrial());
        assertEquals("GOLD", res.getTargetRank());
        assertEquals(6, res.getBillingPeriodMonths());
        verify(subscriptionClient).acknowledgeSubscription(token);
        verify(userRepository).save(argThat(u -> "GOLD".equals(u.getRank())));
    }

    @Test
    @DisplayName("Seam 2: RTDN Renewal for Plan 12M adds 12 Months and sets ACTIVE")
    void testSeam2_Renewal_Plan12M() {
        String token = "seam2_token_12m";
        LocalDateTime expiry = LocalDateTime.now().plusDays(5);
        UserSubscription existingSub = UserSubscription.builder()
                .id(301L)
                .userId(88L)
                .planId(3L)
                .purchaseToken(token)
                .subscriptionState(SubscriptionState.TRIALING)
                .isTrial(true)
                .startTime(LocalDateTime.now().minusDays(25))
                .expiryTime(expiry)
                .autoRenewing(true)
                .build();

        when(userSubscriptionRepository.findByPurchaseToken(token)).thenReturn(Optional.of(existingSub));
        when(planRepository.findById(3L)).thenReturn(Optional.of(plan12M));
        when(userRepository.findById(88L)).thenReturn(Optional.of(user));

        // Event type 2 = SUBSCRIPTION_RENEWED
        rtdnHandlerService.processNotification(token, 2, "{\"notificationType\":2}");

        assertEquals(SubscriptionState.ACTIVE, existingSub.getSubscriptionState());
        assertFalse(existingSub.getIsTrial());
        assertEquals(expiry.plusMonths(12), existingSub.getExpiryTime());
        verify(userRepository).save(argThat(u -> "PLATINUM".equals(u.getRank())));
    }

    @Test
    @DisplayName("Seam 3: Idempotency - Duplicate verify call returns existing active subscription without recharging")
    void testSeam3_Idempotency_DuplicateVerify() {
        String token = "duplicate_token";
        UserSubscription existing = UserSubscription.builder()
                .id(401L)
                .userId(88L)
                .planId(2L)
                .purchaseToken(token)
                .subscriptionState(SubscriptionState.TRIALING)
                .isTrial(true)
                .startTime(LocalDateTime.now())
                .expiryTime(LocalDateTime.now().plusDays(30))
                .build();

        when(userSubscriptionRepository.findByPurchaseToken(token)).thenReturn(Optional.of(existing));
        when(planRepository.findById(2L)).thenReturn(Optional.of(plan6M));

        GooglePurchaseVerifyRequest request = GooglePurchaseVerifyRequest.builder()
                .purchaseToken(token)
                .planCode("GOLD_6M")
                .build();

        UserSubscriptionResponse res = verificationService.verifyAndActivate(88L, request);

        assertNotNull(res);
        assertEquals(401L, res.getId());
        verify(subscriptionClient, never()).getSubscriptionPurchase(any());
        verify(subscriptionClient, never()).acknowledgeSubscription(any());
    }

    @Test
    @DisplayName("Seam 4: Grace Period -> Expired Sequence (Rank preserved then demoted)")
    void testSeam4_GracePeriod_Then_Expired() {
        String token = "lifecycle_token";
        UserSubscription sub = UserSubscription.builder()
                .id(501L)
                .userId(88L)
                .planId(2L)
                .purchaseToken(token)
                .subscriptionState(SubscriptionState.ACTIVE)
                .isTrial(false)
                .startTime(LocalDateTime.now().minusMonths(6))
                .expiryTime(LocalDateTime.now())
                .autoRenewing(true)
                .build();

        when(userSubscriptionRepository.findByPurchaseToken(token)).thenReturn(Optional.of(sub));

        // Step 1: Enters Grace Period (type 6)
        rtdnHandlerService.processNotification(token, 6, "{\"notificationType\":6}");
        assertEquals(SubscriptionState.IN_GRACE_PERIOD, sub.getSubscriptionState());
        verify(userRepository, never()).save(any());

        // Step 2: Fails to recover and expires (type 13)
        when(userRepository.findById(88L)).thenReturn(Optional.of(user));
        rtdnHandlerService.processNotification(token, 13, "{\"notificationType\":13}");
        assertEquals(SubscriptionState.EXPIRED, sub.getSubscriptionState());
        assertFalse(sub.getAutoRenewing());
        verify(userRepository).save(argThat(u -> "MEMBER".equals(u.getRank())));
    }
}
