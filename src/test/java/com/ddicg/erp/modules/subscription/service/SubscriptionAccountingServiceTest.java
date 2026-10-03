package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.payment.model.CustomerPaymentProfile;
import com.ddicg.erp.modules.payment.repository.CustomerPaymentProfileRepository;
import com.ddicg.erp.modules.payment.service.client.PaymentGatewayClient;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.SubscriptionAuditLogRepository;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionAccountingServiceTest {

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private FineractJournalService fineractJournalService;

    @Mock
    private CustomerPaymentProfileRepository profileRepository;

    @Mock
    private SubscriptionPlanRepository planRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private SubscriptionAuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SubscriptionBillingService billingService;

    private User user;
    private SubscriptionPlan plan3M;
    private CustomerPaymentProfile profile;
    private UserSubscription trialSubscription;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(20L)
                .email("accounting_test@example.com")
                .rank(UserRank.GOLD)
                .build();

        plan3M = SubscriptionPlan.builder()
                .id(1L)
                .planCode("GOLD_3M")
                .name("Gói Gold 3 Tháng")
                .targetRank("GOLD")
                .billingPeriodMonths(3)
                .freeTrialDays(30)
                .priceAmount(BigDecimal.valueOf(299000))
                .currency("VND")
                .isActive(true)
                .build();

        profile = CustomerPaymentProfile.builder()
                .id(1L)
                .profileCode("prof_20")
                .userId(20L)
                .gateway("GOOGLE_PAY_INTERNAL")
                .multiUseToken("vault_tok_active_20")
                .fpanLastFour("8888")
                .cardNetwork("MASTERCARD")
                .status("ACTIVE")
                .build();

        trialSubscription = UserSubscription.builder()
                .id(1001L)
                .userId(20L)
                .planId(1L)
                .paymentProfileId("prof_20")
                .subscriptionState(SubscriptionState.TRIALING)
                .isTrial(true)
                .startTime(LocalDateTime.now().minusDays(30))
                .expiryTime(LocalDateTime.now())
                .autoRenewing(true)
                .build();
    }

    @Test
    @DisplayName("Gia hạn định kỳ MIT trừ tiền thành công: chuyển ACTIVE, cộng 3 tháng VÀ tự động hạch toán Fineract")
    void testProcessRenewal_Success_AutoAccounting() {
        when(userSubscriptionRepository.findById(1001L)).thenReturn(Optional.of(trialSubscription));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan3M));
        when(profileRepository.findByProfileCode("prof_20")).thenReturn(Optional.of(profile));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));

        when(paymentGatewayClient.chargeRecurringMit(eq("vault_tok_active_20"), eq(BigDecimal.valueOf(299000)), eq("VND"), anyString()))
                .thenReturn(new PaymentGatewayClient.MitChargeResult(true, "mit_txn_999", null, null));

        ObjectMapper om = new ObjectMapper();
        ObjectNode fakeFineractResp = om.createObjectNode();
        fakeFineractResp.put("entryNumber", "JE-2026-001");
        when(fineractJournalService.recordSale(anyString(), eq(BigDecimal.valueOf(299000)), anyString(), eq(PaymentMethod.CREDIT_CARD)))
                .thenReturn(fakeFineractResp);

        boolean renewed = billingService.processRecurringRenewal(1001L);

        assertTrue(renewed);
        assertEquals(SubscriptionState.ACTIVE, trialSubscription.getSubscriptionState());
        assertFalse(trialSubscription.getIsTrial());
        assertNotNull(trialSubscription.getLastPaymentTime());

        // Xác minh Fineract tự động hạch toán ghi nhận doanh thu
        verify(fineractJournalService).recordSale(
                contains("SUB-1001"),
                eq(BigDecimal.valueOf(299000)),
                contains("Thanh toán định kỳ"),
                eq(PaymentMethod.CREDIT_CARD)
        );
        verify(userSubscriptionRepository).save(trialSubscription);
    }

    @Test
    @DisplayName("Gia hạn định kỳ MIT thất bại: chuyển IN_GRACE_PERIOD và KHÔNG hạch toán Fineract")
    void testProcessRenewal_Failure_NoAccounting() {
        when(userSubscriptionRepository.findById(1001L)).thenReturn(Optional.of(trialSubscription));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan3M));
        when(profileRepository.findByProfileCode("prof_20")).thenReturn(Optional.of(profile));

        when(paymentGatewayClient.chargeRecurringMit(eq("vault_tok_active_20"), eq(BigDecimal.valueOf(299000)), eq("VND"), anyString()))
                .thenReturn(new PaymentGatewayClient.MitChargeResult(false, null, "CARD_DECLINED", "Thẻ bị từ chối"));

        boolean renewed = billingService.processRecurringRenewal(1001L);

        assertFalse(renewed);
        assertEquals(SubscriptionState.IN_GRACE_PERIOD, trialSubscription.getSubscriptionState());

        // Tuyệt đối không gọi Fineract khi chưa thu được tiền
        verify(fineractJournalService, never()).recordSale(anyString(), any(), anyString(), any());
        verify(userSubscriptionRepository).save(trialSubscription);
    }
}
