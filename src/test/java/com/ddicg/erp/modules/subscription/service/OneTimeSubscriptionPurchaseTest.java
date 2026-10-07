package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.subscription.dto.request.OneTimeSubscriptionPurchaseRequest;
import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.SubscriptionAuditLog;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.SubscriptionAuditLogRepository;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OneTimeSubscriptionPurchaseTest {

    @Mock
    private SubscriptionPlanRepository planRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FineractJournalService fineractJournalService;

    @Mock
    private SubscriptionAuditLogRepository auditLogRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Gói Ánh Kim: Trả trước 1 lần qua VNPay -> Cộng dồn 1 tháng + 7 ngày dùng thử khuyến mại (= 37 ngày), autoRenewing=false, rank SILVER")
    void testPurchaseOneTimeAnhKim_BonusDaysCalculated() {
        Long userId = 100L;
        User user = User.builder().id(userId).email("user100@test.com").rank(UserRank.MEMBER).build();

        SubscriptionPlan planAnhKim = SubscriptionPlan.builder()
                .id(1L)
                .planCode("ANH_KIM")
                .name("Gói Ánh Kim")
                .targetRank("SILVER")
                .billingPeriodMonths(1)
                .freeTrialDays(7) // 7 ngày khuyến mại
                .priceAmount(new BigDecimal("30000.00"))
                .currency("VND")
                .build();

        OneTimeSubscriptionPurchaseRequest request = OneTimeSubscriptionPurchaseRequest.builder()
                .planCode("ANH_KIM")
                .paymentMethod(PaymentMethod.VNPAY)
                .transactionReference("VNPAY_TXN_778899")
                .note("Mua 1 lan goi Anh Kim qua VNPay QR")
                .build();

        ObjectNode fineractResp = objectMapper.createObjectNode();
        fineractResp.put("transactionId", "FIN_JE_30K");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(planRepository.findByPlanCode("ANH_KIM")).thenReturn(Optional.of(planAnhKim));
        when(userSubscriptionRepository.findByUserIdAndSubscriptionStateIn(eq(userId), anyList()))
                .thenReturn(Optional.empty());
        when(fineractJournalService.recordSale(eq("VNPAY_TXN_778899"), eq(new BigDecimal("30000.00")), anyString(), eq(PaymentMethod.VNPAY)))
                .thenReturn(fineractResp);
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(inv -> {
            UserSubscription s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });

        UserSubscriptionResponse response = subscriptionService.purchaseOneTimeSubscription(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getPlanCode()).isEqualTo("ANH_KIM");
        assertThat(response.getTargetRank()).isEqualTo("SILVER");
        assertThat(response.getSubscriptionState()).isEqualTo(SubscriptionState.ACTIVE);
        assertThat(response.getIsTrial()).isFalse(); // Đã trả tiền thật -> Không phải trial
        assertThat(response.getAutoRenewing()).isFalse(); // Mua 1 lần -> Không tự gia hạn

        // Kiểm tra thời hạn = 1 tháng + 7 ngày khuyến mại (~37 hoặc 38 ngày tùy tháng)
        long totalDays = ChronoUnit.DAYS.between(response.getStartTime(), response.getExpiryTime());
        assertThat(totalDays).isGreaterThanOrEqualTo(37);

        // Kiểm tra UserRank được nâng lên SILVER
        assertThat(user.getRank()).isEqualTo("SILVER");

        // Kiểm tra Hạch toán Fineract
        verify(fineractJournalService).recordSale(eq("VNPAY_TXN_778899"), eq(new BigDecimal("30000.00")), anyString(), eq(PaymentMethod.VNPAY));

        // Kiểm tra Audit Log
        ArgumentCaptor<SubscriptionAuditLog> logCaptor = ArgumentCaptor.forClass(SubscriptionAuditLog.class);
        verify(auditLogRepository).save(logCaptor.capture());
        SubscriptionAuditLog auditLog = logCaptor.getValue();
        assertThat(auditLog.getEventType()).isEqualTo("ONE_TIME_PURCHASE");
        assertThat(auditLog.getAmount()).isEqualByComparingTo("30000.00");
        assertThat(auditLog.getFineractJournalId()).isEqualTo("FIN_JE_30K");
    }

    @Test
    @DisplayName("Gói Ma Thuật: Trả trước 1 lần qua Thẻ -> Thời hạn 1 tháng, autoRenewing=false, rank DIAMOND")
    void testPurchaseOneTimeMaThuat_NoTrialDays() {
        Long userId = 200L;
        User user = User.builder().id(userId).email("user200@test.com").rank(UserRank.MEMBER).build();

        SubscriptionPlan planMaThuat = SubscriptionPlan.builder()
                .id(2L)
                .planCode("MA_THUAT")
                .name("Gói Ma Thuật")
                .targetRank("DIAMOND")
                .billingPeriodMonths(1)
                .freeTrialDays(0)
                .priceAmount(new BigDecimal("120000.00"))
                .currency("VND")
                .build();

        OneTimeSubscriptionPurchaseRequest request = OneTimeSubscriptionPurchaseRequest.builder()
                .planCode("MA_THUAT")
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .transactionReference("CARD_TXN_120K")
                .note("Mua 1 lan goi Ma Thuat qua the")
                .build();

        ObjectNode fineractResp = objectMapper.createObjectNode();
        fineractResp.put("transactionId", "FIN_JE_120K");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(planRepository.findByPlanCode("MA_THUAT")).thenReturn(Optional.of(planMaThuat));
        when(userSubscriptionRepository.findByUserIdAndSubscriptionStateIn(eq(userId), anyList()))
                .thenReturn(Optional.empty());
        when(fineractJournalService.recordSale(eq("CARD_TXN_120K"), eq(new BigDecimal("120000.00")), anyString(), eq(PaymentMethod.CREDIT_CARD)))
                .thenReturn(fineractResp);
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(inv -> {
            UserSubscription s = inv.getArgument(0);
            s.setId(20L);
            return s;
        });

        UserSubscriptionResponse response = subscriptionService.purchaseOneTimeSubscription(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getPlanCode()).isEqualTo("MA_THUAT");
        assertThat(response.getTargetRank()).isEqualTo("DIAMOND");
        assertThat(response.getSubscriptionState()).isEqualTo(SubscriptionState.ACTIVE);
        assertThat(response.getIsTrial()).isFalse();
        assertThat(response.getAutoRenewing()).isFalse();

        // Kiểm tra UserRank được nâng lên DIAMOND
        assertThat(user.getRank()).isEqualTo("DIAMOND");

        // Kiểm tra Fineract hạch toán
        verify(fineractJournalService).recordSale(eq("CARD_TXN_120K"), eq(new BigDecimal("120000.00")), anyString(), eq(PaymentMethod.CREDIT_CARD));
    }

    @Test
    @DisplayName("Khách hàng đang có gói Active còn hạn -> Mua thêm 1 lần sẽ CỘNG DỒN tiếp vào ngày hết hạn hiện tại")
    void testPurchaseOneTime_CumulativeRenewalOnExistingActiveSubscription() {
        Long userId = 300L;
        User user = User.builder().id(userId).email("user300@test.com").rank(UserRank.SILVER).build();

        LocalDateTime existingExpiry = LocalDateTime.now().plusDays(10);
        UserSubscription existingSub = UserSubscription.builder()
                .id(30L)
                .userId(userId)
                .planId(1L)
                .subscriptionState(SubscriptionState.ACTIVE)
                .startTime(LocalDateTime.now().minusDays(20))
                .expiryTime(existingExpiry)
                .autoRenewing(false)
                .build();

        SubscriptionPlan planAnhKim = SubscriptionPlan.builder()
                .id(1L)
                .planCode("ANH_KIM")
                .name("Gói Ánh Kim")
                .targetRank("SILVER")
                .billingPeriodMonths(1)
                .freeTrialDays(7)
                .priceAmount(new BigDecimal("30000.00"))
                .currency("VND")
                .build();

        OneTimeSubscriptionPurchaseRequest request = OneTimeSubscriptionPurchaseRequest.builder()
                .planCode("ANH_KIM")
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .transactionReference("BANK_RENEW_30K")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(planRepository.findByPlanCode("ANH_KIM")).thenReturn(Optional.of(planAnhKim));
        when(userSubscriptionRepository.findByUserIdAndSubscriptionStateIn(eq(userId), anyList()))
                .thenReturn(Optional.of(existingSub));
        when(fineractJournalService.recordSale(anyString(), any(), anyString(), any()))
                .thenReturn(objectMapper.createObjectNode().put("transactionId", "TXN_CUMULATIVE"));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

        UserSubscriptionResponse response = subscriptionService.purchaseOneTimeSubscription(userId, request);

        assertThat(response).isNotNull();
        // Expiry mới phải được cộng dồn từ existingExpiry (10 ngày nữa + 1 tháng + 7 ngày)
        assertThat(response.getExpiryTime()).isAfter(existingExpiry.plusMonths(1));
    }

    @Test
    @DisplayName("Mã gói không tồn tại -> Ném BusinessException")
    void testPurchaseOneTime_PlanNotFound_ThrowsException() {
        Long userId = 400L;
        User user = User.builder().id(userId).email("user400@test.com").build();

        OneTimeSubscriptionPurchaseRequest request = OneTimeSubscriptionPurchaseRequest.builder()
                .planCode("INVALID_PLAN")
                .paymentMethod(PaymentMethod.VNPAY)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(planRepository.findByPlanCode("INVALID_PLAN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.purchaseOneTimeSubscription(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không tìm thấy gói dịch vụ");
    }
}
