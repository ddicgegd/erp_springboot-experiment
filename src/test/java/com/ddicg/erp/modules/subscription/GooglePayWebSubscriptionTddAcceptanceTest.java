package com.ddicg.erp.modules.subscription;

import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.payment.model.CustomerPaymentProfile;
import com.ddicg.erp.modules.payment.repository.CustomerPaymentProfileRepository;
import com.ddicg.erp.modules.payment.service.client.PaymentGatewayClient;
import com.ddicg.erp.modules.subscription.dto.request.GooglePayWebRegisterRequest;
import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.SubscriptionAuditLogRepository;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import com.ddicg.erp.modules.subscription.service.GooglePayWebService;
import com.ddicg.erp.modules.subscription.service.SubscriptionBillingService;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E2E Seam-Based Acceptance Suite for Google Pay Web & Fineract Accounting
 */
@ExtendWith(MockitoExtension.class)
class GooglePayWebSubscriptionTddAcceptanceTest {

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
    private GooglePayWebService googlePayWebService;

    @InjectMocks
    private SubscriptionBillingService billingService;

    private User user;
    private SubscriptionPlan plan12M;
    private CustomerPaymentProfile profile;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(77L)
                .email("subscriber_e2e@example.com")
                .rank(UserRank.MEMBER)
                .build();

        plan12M = SubscriptionPlan.builder()
                .id(3L)
                .planCode("PLATINUM_12M")
                .name("Gói Platinum 12 Tháng")
                .targetRank("PLATINUM")
                .billingPeriodMonths(12)
                .freeTrialDays(30)
                .priceAmount(BigDecimal.valueOf(990000))
                .currency("VND")
                .isActive(true)
                .build();

        profile = CustomerPaymentProfile.builder()
                .id("prof_77_vault")
                .userId(77L)
                .gateway("GOOGLE_PAY_INTERNAL")
                .multiUseToken("vault_tok_platinum_77")
                .fpanLastFour("1111")
                .cardNetwork("VISA")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("Seam 1: Web Registration Free Trial 30 days - Vaults token, updates Rank, KHÔNG gọi Fineract")
    void testSeam1_WebRegistration_Trial_NoAccounting() {
        GooglePayWebRegisterRequest req = GooglePayWebRegisterRequest.builder()
                .planCode("PLATINUM_12M")
                .paymentToken("encrypted_gpay_web_token")
                .cardNetwork("VISA")
                .lastFour("1111")
                .build();

        when(userRepository.findById(77L)).thenReturn(Optional.of(user));
        when(planRepository.findByPlanCode("PLATINUM_12M")).thenReturn(Optional.of(plan12M));
        when(userSubscriptionRepository.findByUserIdAndSubscriptionStateIn(eq(77L), anyList()))
                .thenReturn(Optional.empty());

        when(paymentGatewayClient.vaultPaymentMethod(eq(77L), anyString(), eq("VISA"), eq("1111")))
                .thenReturn(profile);
        when(profileRepository.save(any(CustomerPaymentProfile.class))).thenReturn(profile);
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(i -> {
            UserSubscription s = i.getArgument(0);
            s.setId(7701L);
            return s;
        });

        UserSubscriptionResponse res = googlePayWebService.registerTrialSubscription(77L, req);

        assertNotNull(res);
        assertEquals(SubscriptionState.TRIALING, res.getSubscriptionState());
        assertTrue(res.getIsTrial());
        assertEquals("PLATINUM", res.getTargetRank());

        // Nghiêm ngặt: Gói 0 đồng tuyệt đối không gọi Fineract
        verify(fineractJournalService, never()).recordSale(anyString(), any(), anyString(), any());
        verify(userRepository).save(argThat(u -> "PLATINUM".equals(u.getRank())));
    }

    @Test
    @DisplayName("Seam 2: MIT Renewal Success (+12M) - Chuyển ACTIVE và TỰ ĐỘNG hạch toán Fineract")
    void testSeam2_Renewal_Success_AutoFineract() {
        UserSubscription sub = UserSubscription.builder()
                .id(7701L)
                .userId(77L)
                .planId(3L)
                .paymentProfileId("prof_77_vault")
                .subscriptionState(SubscriptionState.TRIALING)
                .isTrial(true)
                .startTime(LocalDateTime.now().minusDays(30))
                .expiryTime(LocalDateTime.now())
                .autoRenewing(true)
                .build();

        when(userSubscriptionRepository.findById(7701L)).thenReturn(Optional.of(sub));
        when(planRepository.findById(3L)).thenReturn(Optional.of(plan12M));
        when(profileRepository.findById("prof_77_vault")).thenReturn(Optional.of(profile));
        when(userRepository.findById(77L)).thenReturn(Optional.of(user));

        when(paymentGatewayClient.chargeRecurringMit(eq("vault_tok_platinum_77"), eq(BigDecimal.valueOf(990000)), eq("VND"), anyString()))
                .thenReturn(new PaymentGatewayClient.MitChargeResult(true, "mit_txn_platinum_77", null, null));

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode fakeResp = mapper.createObjectNode();
        fakeResp.put("entryNumber", "JE-FINERACT-PLATINUM-77");
        when(fineractJournalService.recordSale(anyString(), eq(BigDecimal.valueOf(990000)), anyString(), eq(PaymentMethod.CREDIT_CARD)))
                .thenReturn(fakeResp);

        boolean ok = billingService.processRecurringRenewal(7701L);

        assertTrue(ok);
        assertEquals(SubscriptionState.ACTIVE, sub.getSubscriptionState());
        assertFalse(sub.getIsTrial());
        assertEquals("JE-FINERACT-PLATINUM-77", sub.getLastFineractJournalId());

        // Bắt buộc gọi Fineract hạch toán doanh thu bán hàng gói định kỳ
        verify(fineractJournalService).recordSale(
                contains("SUB-7701"),
                eq(BigDecimal.valueOf(990000)),
                contains("Thanh toán định kỳ"),
                eq(PaymentMethod.CREDIT_CARD)
        );
        verify(userRepository).save(argThat(u -> "PLATINUM".equals(u.getRank())));
    }

    @Test
    @DisplayName("Seam 3: MIT Renewal Decline - Vào IN_GRACE_PERIOD và KHÔNG hạch toán Fineract")
    void testSeam3_Renewal_Decline_NoAccounting() {
        UserSubscription sub = UserSubscription.builder()
                .id(7701L)
                .userId(77L)
                .planId(3L)
                .paymentProfileId("prof_77_vault")
                .subscriptionState(SubscriptionState.ACTIVE)
                .isTrial(false)
                .startTime(LocalDateTime.now().minusMonths(12))
                .expiryTime(LocalDateTime.now())
                .autoRenewing(true)
                .build();

        when(userSubscriptionRepository.findById(7701L)).thenReturn(Optional.of(sub));
        when(planRepository.findById(3L)).thenReturn(Optional.of(plan12M));
        when(profileRepository.findById("prof_77_vault")).thenReturn(Optional.of(profile));

        when(paymentGatewayClient.chargeRecurringMit(eq("vault_tok_platinum_77"), eq(BigDecimal.valueOf(990000)), eq("VND"), anyString()))
                .thenReturn(new PaymentGatewayClient.MitChargeResult(false, null, "INSUFFICIENT_FUNDS", "Không đủ tiền"));

        boolean ok = billingService.processRecurringRenewal(7701L);

        assertFalse(ok);
        assertEquals(SubscriptionState.IN_GRACE_PERIOD, sub.getSubscriptionState());
        verify(fineractJournalService, never()).recordSale(anyString(), any(), anyString(), any());
    }
}
