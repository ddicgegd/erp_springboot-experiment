package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.subscription.dto.request.GooglePurchaseVerifyRequest;
import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.SubscriptionAuditLog;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.SubscriptionAuditLogRepository;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
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

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

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

    private SubscriptionPlan plan3M;
    private User user;

    @BeforeEach
    void setUp() {
        plan3M = SubscriptionPlan.builder()
                .id(1L)
                .planCode("GOLD_3M")
                .googleProductId("sub_gold")
                .basePlanId("plan-3-months")
                .targetRank("GOLD")
                .billingPeriodMonths(3)
                .freeTrialDays(30)
                .priceAmount(BigDecimal.valueOf(299000))
                .currency("VND")
                .isActive(true)
                .build();

        user = User.builder()
                .id(10L)
                .email("test@example.com")
                .rank(UserRank.MEMBER)
                .build();
    }

    @Test
    @DisplayName("Verify purchase token -> free trial 1 month -> State TRIALING & rank GOLD")
    void testVerifyAndActivate_FreeTrial() {
        String token = "valid_trial_token";
        GooglePurchaseVerifyRequest request = GooglePurchaseVerifyRequest.builder()
                .purchaseToken(token)
                .planCode("GOLD_3M")
                .orderId("GPA.1111-2222")
                .build();

        Instant now = Instant.now();
        GooglePlaySubscriptionClient.SubscriptionPurchaseV2 purchaseV2 = GooglePlaySubscriptionClient.SubscriptionPurchaseV2.builder()
                .subscriptionState("SUBSCRIPTION_STATE_ACTIVE")
                .startTime(now)
                .expiryTime(now.plus(30, ChronoUnit.DAYS))
                .acknowledgementState(false)
                .lineItems(List.of(GooglePlaySubscriptionClient.LineItem.builder()
                        .offerId("free-trial-offer")
                        .productId("sub_gold")
                        .basePlanId("plan-3-months")
                        .build()))
                .build();

        when(userSubscriptionRepository.findByPurchaseToken(token)).thenReturn(Optional.empty());
        when(planRepository.findByPlanCode("GOLD_3M")).thenReturn(Optional.of(plan3M));
        when(subscriptionClient.getSubscriptionPurchase(token)).thenReturn(purchaseV2);
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(invocation -> {
            UserSubscription sub = invocation.getArgument(0);
            sub.setId(100L);
            return sub;
        });
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        UserSubscriptionResponse response = verificationService.verifyAndActivate(10L, request);

        assertNotNull(response);
        assertEquals(SubscriptionState.TRIALING, response.getSubscriptionState());
        assertTrue(response.getIsTrial());
        assertEquals("GOLD", response.getTargetRank());
        verify(subscriptionClient).acknowledgeSubscription(token);
        verify(userRepository).save(argThat(u -> "GOLD".equals(u.getRank())));
    }

    @Test
    @DisplayName("RTDN RENEWED: Gia hạn thêm 3 tháng và chuyển ACTIVE")
    void testRtdn_SubscriptionRenewed() {
        String token = "active_token";
        LocalDateTime expiry = LocalDateTime.now().plusDays(10);
        UserSubscription existingSub = UserSubscription.builder()
                .id(100L)
                .userId(10L)
                .planId(1L)
                .purchaseToken(token)
                .subscriptionState(SubscriptionState.TRIALING)
                .isTrial(true)
                .startTime(LocalDateTime.now().minusDays(20))
                .expiryTime(expiry)
                .autoRenewing(true)
                .build();

        when(userSubscriptionRepository.findByPurchaseToken(token)).thenReturn(Optional.of(existingSub));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan3M));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        // 2: SUBSCRIPTION_RENEWED
        rtdnHandlerService.processNotification(token, 2, "{\"notificationType\": 2}");

        assertEquals(SubscriptionState.ACTIVE, existingSub.getSubscriptionState());
        assertFalse(existingSub.getIsTrial());
        assertEquals(expiry.plusMonths(3), existingSub.getExpiryTime());
        verify(userSubscriptionRepository).save(existingSub);
        verify(auditLogRepository).save(any(SubscriptionAuditLog.class));
    }

    @Test
    @DisplayName("RTDN IN_GRACE_PERIOD: Không hạ rank trong thời gian ân hạn")
    void testRtdn_GracePeriod() {
        String token = "grace_token";
        UserSubscription existingSub = UserSubscription.builder()
                .id(100L)
                .userId(10L)
                .planId(1L)
                .purchaseToken(token)
                .subscriptionState(SubscriptionState.ACTIVE)
                .isTrial(false)
                .startTime(LocalDateTime.now().minusMonths(3))
                .expiryTime(LocalDateTime.now())
                .autoRenewing(true)
                .build();

        when(userSubscriptionRepository.findByPurchaseToken(token)).thenReturn(Optional.of(existingSub));

        // 6: SUBSCRIPTION_IN_GRACE_PERIOD
        rtdnHandlerService.processNotification(token, 6, "{\"notificationType\": 6}");

        assertEquals(SubscriptionState.IN_GRACE_PERIOD, existingSub.getSubscriptionState());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("RTDN ON_HOLD hoặc EXPIRED: Hạ rank về MEMBER")
    void testRtdn_ExpiredOrOnHold() {
        String token = "expired_token";
        UserSubscription existingSub = UserSubscription.builder()
                .id(100L)
                .userId(10L)
                .planId(1L)
                .purchaseToken(token)
                .subscriptionState(SubscriptionState.ACTIVE)
                .isTrial(false)
                .startTime(LocalDateTime.now().minusMonths(3))
                .expiryTime(LocalDateTime.now())
                .autoRenewing(true)
                .build();

        when(userSubscriptionRepository.findByPurchaseToken(token)).thenReturn(Optional.of(existingSub));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        // 13: SUBSCRIPTION_EXPIRED
        rtdnHandlerService.processNotification(token, 13, "{\"notificationType\": 13}");

        assertEquals(SubscriptionState.EXPIRED, existingSub.getSubscriptionState());
        assertFalse(existingSub.getAutoRenewing());
        verify(userRepository).save(argThat(u -> "MEMBER".equals(u.getRank())));
    }
}
