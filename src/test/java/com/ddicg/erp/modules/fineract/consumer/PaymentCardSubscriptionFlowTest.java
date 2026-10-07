package com.ddicg.erp.modules.fineract.consumer;

import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.order.model.Payment;
import com.ddicg.erp.modules.order.repository.OrderRepository;
import com.ddicg.erp.modules.order.repository.PaymentRepository;
import com.ddicg.erp.modules.payment.dto.CardPaymentResultEvent;
import com.ddicg.erp.modules.subscription.model.SubscriptionAuditLog;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.SubscriptionAuditLogRepository;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Luồng test 3 (Task 3B - Subscription Flow): Kiểm thử thanh toán Subscription, UserRank, Fineract & Audit Log")
class PaymentCardSubscriptionFlowTest {

    @Mock
    private FineractJournalService fineractJournalService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private SubscriptionPlanRepository planRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionAuditLogRepository auditLogRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private PaymentCardFineractConsumer consumer;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        consumer.setRedisTemplate(null);

        sampleUser = new User();
        sampleUser.setId(50L);
        sampleUser.setRank(UserRank.MEMBER);
    }

    @Nested
    @DisplayName("Kiểm thử nâng cấp gói cước cao cấp DIAMOND")
    class DiamondPlanSubscriptionTests {

        @Test
        @DisplayName("Thanh toán gói DIAMOND: Cập nhật UserRank DIAMOND, trạng thái ACTIVE, hạch toán Fineract và ghi Audit Log")
        void testSubscriptionPayment_DiamondPlan_Success() {
            Long subId = 500L;
            Long planId = 88L;
            LocalDateTime now = LocalDateTime.now();

            SubscriptionPlan diamondPlan = SubscriptionPlan.builder()
                    .id(planId)
                    .planCode("PLAN_DIAMOND")
                    .name("Gói Kim Cương")
                    .targetRank("DIAMOND")
                    .billingPeriodMonths(12)
                    .priceAmount(BigDecimal.valueOf(12000000))
                    .currency("VND")
                    .build();

            UserSubscription diamondSub = UserSubscription.builder()
                    .id(subId)
                    .userId(50L)
                    .planId(planId)
                    .subscriptionState(SubscriptionState.PENDING)
                    .isTrial(true)
                    .startTime(now.minusDays(7))
                    .expiryTime(now.minusDays(1))
                    .build();

            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .transactionId("TXN-SUB-DIAMOND-999")
                    .cardType("VISA")
                    .cardLastFour("9999")
                    .grossAmount(BigDecimal.valueOf(12000000))
                    .feeAmount(BigDecimal.valueOf(120000))
                    .netAmount(BigDecimal.valueOf(11880000))
                    .currency("VND")
                    .paidAt(now)
                    .subscriptionId(subId)
                    .isSubscription(true)
                    .build();

            when(userSubscriptionRepository.findById(subId)).thenReturn(Optional.of(diamondSub));
            when(planRepository.findById(planId)).thenReturn(Optional.of(diamondPlan));
            when(userRepository.findById(50L)).thenReturn(Optional.of(sampleUser));
            when(paymentRepository.existsByTransactionCode("TXN-SUB-DIAMOND-999")).thenReturn(false);

            ObjectNode responseNode = JsonNodeFactory.instance.objectNode();
            responseNode.put("entryNumber", "JE-DIAMOND-8888");
            when(fineractJournalService.recordSale(eq("TXN-SUB-DIAMOND-999"), eq(BigDecimal.valueOf(12000000)), anyString(), eq(PaymentMethod.VNPAY)))
                    .thenReturn(responseNode);

            consumer.consume(event, null);

            // 1. Kiểm tra UserRank được duy trì / nâng cấp thành DIAMOND
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(1)).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getUserRank()).isEqualTo(UserRank.DIAMOND);

            // 2. Kiểm tra UserSubscription chuyển sang ACTIVE, isTrial = false, thời hạn 12 tháng, journalId
            ArgumentCaptor<UserSubscription> subCaptor = ArgumentCaptor.forClass(UserSubscription.class);
            verify(userSubscriptionRepository, times(1)).save(subCaptor.capture());
            UserSubscription savedSub = subCaptor.getValue();
            assertThat(savedSub.getSubscriptionState()).isEqualTo(SubscriptionState.ACTIVE);
            assertThat(savedSub.getIsTrial()).isFalse();
            assertThat(savedSub.getLastFineractJournalId()).isEqualTo("JE-DIAMOND-8888");
            assertThat(savedSub.getExpiryTime()).isAfterOrEqualTo(now.plusMonths(12).minusSeconds(5));

            // 3. Kiểm tra hạch toán Fineract recordSale
            verify(fineractJournalService, times(1)).recordSale(
                    eq("TXN-SUB-DIAMOND-999"),
                    eq(BigDecimal.valueOf(12000000)),
                    contains("PLAN_DIAMOND"),
                    eq(PaymentMethod.VNPAY)
            );

            // 4. Kiểm tra ghi SubscriptionAuditLog đầy đủ
            ArgumentCaptor<SubscriptionAuditLog> auditCaptor = ArgumentCaptor.forClass(SubscriptionAuditLog.class);
            verify(auditLogRepository, times(1)).save(auditCaptor.capture());
            SubscriptionAuditLog auditLog = auditCaptor.getValue();
            assertThat(auditLog.getUserId()).isEqualTo(50L);
            assertThat(auditLog.getSubscriptionId()).isEqualTo(subId);
            assertThat(auditLog.getEventType()).isEqualTo("SUBSCRIPTION_CARD_PAYMENT_COMPLETED");
            assertThat(auditLog.getPreviousState()).isEqualTo(SubscriptionState.PENDING.name());
            assertThat(auditLog.getNewState()).isEqualTo(SubscriptionState.ACTIVE.name());
            assertThat(auditLog.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(12000000));
            assertThat(auditLog.getCurrency()).isEqualTo("VND");
            assertThat(auditLog.getFineractJournalId()).isEqualTo("JE-DIAMOND-8888");

            // 5. Kiểm tra Payment entity lưu vết giao dịch
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(1)).save(paymentCaptor.capture());
            Payment savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getTransactionCode()).isEqualTo("TXN-SUB-DIAMOND-999");
            assertThat(savedPayment.getStatus()).isEqualTo("PAID");
            assertThat(savedPayment.getProvider()).isEqualTo("VNPAY");
            assertThat(savedPayment.getCardType()).isEqualTo("VISA");
            assertThat(savedPayment.getBankCode()).isEqualTo("****9999");
        }
    }

    @Nested
    @DisplayName("Kiểm thử nâng cấp gói cước SILVER")
    class SilverPlanSubscriptionTests {

        @Test
        @DisplayName("Thanh toán gói SILVER: Cập nhật UserRank SILVER, trạng thái ACTIVE, hạch toán Fineract và ghi Audit Log")
        void testSubscriptionPayment_SilverPlan_Success() {
            Long subId = 300L;
            Long planId = 33L;
            LocalDateTime now = LocalDateTime.now();

            SubscriptionPlan silverPlan = SubscriptionPlan.builder()
                    .id(planId)
                    .planCode("PLAN_SILVER")
                    .name("Gói Bạc")
                    .targetRank("SILVER")
                    .billingPeriodMonths(1)
                    .priceAmount(BigDecimal.valueOf(99000))
                    .currency("VND")
                    .build();

            UserSubscription silverSub = UserSubscription.builder()
                    .id(subId)
                    .userId(50L)
                    .planId(planId)
                    .subscriptionState(SubscriptionState.EXPIRED)
                    .isTrial(false)
                    .expiryTime(now.minusDays(5))
                    .build();

            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .transactionId("TXN-SUB-SILVER-111")
                    .cardType("MASTERCARD")
                    .cardLastFour("1111")
                    .grossAmount(BigDecimal.valueOf(99000))
                    .currency("VND")
                    .paidAt(now)
                    .subscriptionId(subId)
                    .isSubscription(true)
                    .build();

            when(userSubscriptionRepository.findById(subId)).thenReturn(Optional.of(silverSub));
            when(planRepository.findById(planId)).thenReturn(Optional.of(silverPlan));
            when(userRepository.findById(50L)).thenReturn(Optional.of(sampleUser));
            when(paymentRepository.existsByTransactionCode("TXN-SUB-SILVER-111")).thenReturn(false);

            ObjectNode responseNode = JsonNodeFactory.instance.objectNode();
            responseNode.put("resourceId", "RES-SILVER-777");
            when(fineractJournalService.recordSale(eq("TXN-SUB-SILVER-111"), eq(BigDecimal.valueOf(99000)), anyString(), eq(PaymentMethod.VNPAY)))
                    .thenReturn(responseNode);

            consumer.consume(event, null);

            // Kiểm tra UserRank cập nhật thành SILVER
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(1)).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getUserRank()).isEqualTo(UserRank.SILVER);

            // Kiểm tra trạng thái và journalId trên Subscription
            ArgumentCaptor<UserSubscription> subCaptor = ArgumentCaptor.forClass(UserSubscription.class);
            verify(userSubscriptionRepository, times(1)).save(subCaptor.capture());
            assertThat(subCaptor.getValue().getSubscriptionState()).isEqualTo(SubscriptionState.ACTIVE);
            assertThat(subCaptor.getValue().getLastFineractJournalId()).isEqualTo("RES-SILVER-777");

            // Kiểm tra Audit Log có previousState = EXPIRED và newState = ACTIVE
            ArgumentCaptor<SubscriptionAuditLog> auditCaptor = ArgumentCaptor.forClass(SubscriptionAuditLog.class);
            verify(auditLogRepository, times(1)).save(auditCaptor.capture());
            assertThat(auditCaptor.getValue().getPreviousState()).isEqualTo(SubscriptionState.EXPIRED.name());
            assertThat(auditCaptor.getValue().getNewState()).isEqualTo(SubscriptionState.ACTIVE.name());
        }
    }

    @Nested
    @DisplayName("Kiểm thử Gia hạn lũy kế & Cổng thanh toán quốc tế")
    class AdvancedSubscriptionScenarios {

        @Test
        @DisplayName("Gia hạn khi Subscription vẫn còn hạn (Active): Cộng dồn thời hạn mới vào expiryTime cũ")
        void testSubscriptionPayment_CumulativeRenewal_WhenAlreadyActive() {
            Long subId = 400L;
            Long planId = 15L;
            LocalDateTime futureExpiry = LocalDateTime.now().plusDays(20);

            SubscriptionPlan plan = SubscriptionPlan.builder()
                    .id(planId)
                    .planCode("PLAN_PRO_1M")
                    .billingPeriodMonths(1)
                    .priceAmount(BigDecimal.valueOf(150000))
                    .build();

            UserSubscription activeSub = UserSubscription.builder()
                    .id(subId)
                    .userId(50L)
                    .planId(planId)
                    .subscriptionState(SubscriptionState.ACTIVE)
                    .expiryTime(futureExpiry)
                    .build();

            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .transactionId("TXN-RENEW-CUMULATIVE")
                    .grossAmount(BigDecimal.valueOf(150000))
                    .subscriptionId(subId)
                    .isSubscription(true)
                    .build();

            when(userSubscriptionRepository.findById(subId)).thenReturn(Optional.of(activeSub));
            when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
            when(paymentRepository.existsByTransactionCode("TXN-RENEW-CUMULATIVE")).thenReturn(false);

            consumer.consume(event, null);

            ArgumentCaptor<UserSubscription> subCaptor = ArgumentCaptor.forClass(UserSubscription.class);
            verify(userSubscriptionRepository, times(1)).save(subCaptor.capture());
            UserSubscription updatedSub = subCaptor.getValue();

            // Expiry mới phải được cộng dồn từ futureExpiry chứ không phải từ now
            assertThat(updatedSub.getExpiryTime()).isEqualTo(futureExpiry.plusMonths(1));
        }

        @Test
        @DisplayName("Thanh toán Subscription qua cổng PayPal: Phân giải PaymentMethod.PAYPAL và hạch toán USD")
        void testSubscriptionPayment_PayPalGateway_Success() {
            Long subId = 401L;
            Long planId = 16L;

            SubscriptionPlan plan = SubscriptionPlan.builder()
                    .id(planId)
                    .planCode("PLAN_GLOBAL")
                    .billingPeriodMonths(1)
                    .priceAmount(BigDecimal.valueOf(15.99))
                    .currency("USD")
                    .build();

            UserSubscription sub = UserSubscription.builder()
                    .id(subId)
                    .userId(50L)
                    .planId(planId)
                    .subscriptionState(SubscriptionState.PENDING)
                    .build();

            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("PAYPAL")
                    .transactionId("TXN-PAYPAL-SUB-001")
                    .grossAmount(BigDecimal.valueOf(15.99))
                    .currency("USD")
                    .subscriptionId(subId)
                    .isSubscription(true)
                    .build();

            when(userSubscriptionRepository.findById(subId)).thenReturn(Optional.of(sub));
            when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
            when(paymentRepository.existsByTransactionCode("TXN-PAYPAL-SUB-001")).thenReturn(false);

            consumer.consume(event, null);

            verify(fineractJournalService, times(1)).recordSale(
                    eq("TXN-PAYPAL-SUB-001"),
                    eq(BigDecimal.valueOf(15.99)),
                    anyString(),
                    eq(PaymentMethod.PAYPAL)
            );
        }

        @Test
        @DisplayName("Fineract Journal Service gặp lỗi ngoại lệ: Consumer bắt lỗi an toàn, Subscription vẫn được cập nhật ACTIVE")
        void testSubscriptionPayment_FineractExceptionHandledGracefully() {
            Long subId = 402L;
            Long planId = 17L;

            SubscriptionPlan plan = SubscriptionPlan.builder()
                    .id(planId)
                    .planCode("PLAN_STANDARD")
                    .billingPeriodMonths(1)
                    .priceAmount(BigDecimal.valueOf(100000))
                    .build();

            UserSubscription sub = UserSubscription.builder()
                    .id(subId)
                    .userId(50L)
                    .planId(planId)
                    .subscriptionState(SubscriptionState.PENDING)
                    .build();

            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .transactionId("TXN-SUB-FINERACT-ERR")
                    .grossAmount(BigDecimal.valueOf(100000))
                    .subscriptionId(subId)
                    .isSubscription(true)
                    .build();

            when(userSubscriptionRepository.findById(subId)).thenReturn(Optional.of(sub));
            when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
            when(paymentRepository.existsByTransactionCode("TXN-SUB-FINERACT-ERR")).thenReturn(false);

            when(fineractJournalService.recordSale(anyString(), any(), anyString(), any()))
                    .thenThrow(new RuntimeException("Fineract 503 Unavailable"));

            // Consumer không throw exception
            consumer.consume(event, null);

            // Subscription vẫn được kích hoạt ACTIVE và lưu vào DB
            ArgumentCaptor<UserSubscription> subCaptor = ArgumentCaptor.forClass(UserSubscription.class);
            verify(userSubscriptionRepository, times(1)).save(subCaptor.capture());
            assertThat(subCaptor.getValue().getSubscriptionState()).isEqualTo(SubscriptionState.ACTIVE);

            // Audit log và Payment record vẫn được lưu
            verify(auditLogRepository, times(1)).save(any(SubscriptionAuditLog.class));
            verify(paymentRepository, times(1)).save(any(Payment.class));
        }

        @Test
        @DisplayName("Thiếu subscriptionId hoặc Subscription không tồn tại: Bỏ qua an toàn không throw exception")
        void testSubscriptionPayment_MissingOrInvalidSubId_SafeIgnore() {
            CardPaymentResultEvent missingIdEvent = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .transactionId("TXN-MISSING-SUB-ID")
                    .isSubscription(true)
                    .subscriptionId(null)
                    .build();

            consumer.consume(missingIdEvent, null);
            verifyNoInteractions(fineractJournalService);

            CardPaymentResultEvent notFoundEvent = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .transactionId("TXN-NOT-FOUND-SUB")
                    .isSubscription(true)
                    .subscriptionId(999999L)
                    .build();

            when(userSubscriptionRepository.findById(999999L)).thenReturn(Optional.empty());

            consumer.consume(notFoundEvent, null);
            verifyNoInteractions(fineractJournalService);
        }
    }
}
