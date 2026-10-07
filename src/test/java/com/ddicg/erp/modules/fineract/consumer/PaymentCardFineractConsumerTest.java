package com.ddicg.erp.modules.fineract.consumer;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.order.model.Order;
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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCardFineractConsumerTest {

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

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private PaymentCardFineractConsumer consumer;

    private UserSubscription sampleSub;
    private SubscriptionPlan samplePlan;
    private User sampleUser;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        consumer.setRedisTemplate(null); // Default without Redis to test baseline DB/in-memory flow

        sampleSub = UserSubscription.builder()
                .id(100L)
                .userId(10L)
                .planId(5L)
                .subscriptionState(SubscriptionState.PENDING)
                .isTrial(true)
                .startTime(LocalDateTime.now().minusDays(30))
                .expiryTime(LocalDateTime.now().minusDays(1))
                .autoRenewing(true)
                .build();

        samplePlan = SubscriptionPlan.builder()
                .id(5L)
                .planCode("PLAN_GOLD")
                .name("Gói Vàng")
                .targetRank("GOLD")
                .billingPeriodMonths(1)
                .priceAmount(BigDecimal.valueOf(199000))
                .currency("VND")
                .build();

        sampleUser = new User();
        sampleUser.setId(10L);
        sampleUser.setRank(UserRank.BRONZE);

        sampleOrder = Order.builder()
                .orderNumber("ORD-TEST-001")
                .status(new ArrayList<>())
                .currentStatus(OrderStatus.PENDING)
                .totalAmount(250000.0)
                .build();
        sampleOrder.getStatus().add(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Xử lý thanh toán thẻ cho Subscription: Kích hoạt ACTIVE, gia hạn expiry, duy trì UserRank và hạch toán Fineract")
    void testConsume_SubscriptionPayment_Success() {
        CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                .eventType("PAYMENT_CARD_COMPLETED")
                .gateway("VNPAY")
                .transactionId("TXN-SUB-VNPAY-01")
                .cardType("VISA")
                .cardLastFour("4242")
                .grossAmount(BigDecimal.valueOf(199000))
                .feeAmount(BigDecimal.valueOf(3000))
                .netAmount(BigDecimal.valueOf(196000))
                .currency("VND")
                .paidAt(LocalDateTime.now())
                .subscriptionId(100L)
                .isSubscription(true)
                .build();

        when(userSubscriptionRepository.findById(100L)).thenReturn(Optional.of(sampleSub));
        when(planRepository.findById(5L)).thenReturn(Optional.of(samplePlan));
        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
        when(paymentRepository.existsByTransactionCode("TXN-SUB-VNPAY-01")).thenReturn(false);

        ObjectNode fineractResp = JsonNodeFactory.instance.objectNode();
        fineractResp.put("entryNumber", "JE-12345");
        when(fineractJournalService.recordSale(eq("TXN-SUB-VNPAY-01"), eq(BigDecimal.valueOf(199000)), anyString(), eq(PaymentMethod.VNPAY)))
                .thenReturn(fineractResp);

        consumer.consume(event, null);

        // Verify Fineract accounting
        verify(fineractJournalService, times(1)).recordSale(
                eq("TXN-SUB-VNPAY-01"),
                eq(BigDecimal.valueOf(199000)),
                contains("PLAN_GOLD"),
                eq(PaymentMethod.VNPAY)
        );

        // Verify UserSubscription update
        ArgumentCaptor<UserSubscription> subCaptor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository, times(1)).save(subCaptor.capture());
        UserSubscription savedSub = subCaptor.getValue();
        assertThat(savedSub.getSubscriptionState()).isEqualTo(SubscriptionState.ACTIVE);
        assertThat(savedSub.getIsTrial()).isFalse();
        assertThat(savedSub.getLastFineractJournalId()).isEqualTo("JE-12345");
        assertThat(savedSub.getExpiryTime()).isAfter(LocalDateTime.now());

        // Verify UserRank maintained
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUserRank()).isEqualTo(UserRank.GOLD);

        // Verify Audit log saved
        verify(auditLogRepository, times(1)).save(any(SubscriptionAuditLog.class));

        // Verify Payment record saved
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(1)).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getTransactionCode()).isEqualTo("TXN-SUB-VNPAY-01");
        assertThat(savedPayment.getStatus()).isEqualTo("PAID");
        assertThat(savedPayment.getProvider()).isEqualTo("VNPAY");
    }

    @Test
    @DisplayName("Xử lý thanh toán Subscription qua cổng PayPal: Phân giải PaymentMethod.PAYPAL")
    void testConsume_SubscriptionPayment_PayPalGateway() {
        CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                .eventType("PAYMENT_CARD_COMPLETED")
                .gateway("PAYPAL")
                .transactionId("TXN-SUB-PAYPAL-02")
                .grossAmount(BigDecimal.valueOf(10))
                .currency("USD")
                .paidAt(LocalDateTime.now())
                .subscriptionId(100L)
                .isSubscription(true)
                .build();

        when(userSubscriptionRepository.findById(100L)).thenReturn(Optional.of(sampleSub));
        when(planRepository.findById(5L)).thenReturn(Optional.of(samplePlan));
        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
        when(paymentRepository.existsByTransactionCode("TXN-SUB-PAYPAL-02")).thenReturn(false);

        consumer.consume(event, null);

        verify(fineractJournalService, times(1)).recordSale(
                eq("TXN-SUB-PAYPAL-02"),
                eq(BigDecimal.valueOf(10)),
                anyString(),
                eq(PaymentMethod.PAYPAL)
        );
    }

    @Test
    @DisplayName("Xử lý thanh toán thẻ cho Order thường: Hạch toán Fineract recordSale và chuyển Order sang CONFIRMED (PAID)")
    void testConsume_OrderPayment_Success() {
        CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                .eventType("PAYMENT_CARD_COMPLETED")
                .gateway("VNPAY")
                .orderNumber("ORD-TEST-001")
                .transactionId("TXN-ORD-001")
                .grossAmount(BigDecimal.valueOf(250000.0))
                .currency("VND")
                .paidAt(LocalDateTime.now())
                .isSubscription(false)
                .build();

        when(orderRepository.findByOrderNumber("ORD-TEST-001")).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.existsByTransactionCode("TXN-ORD-001")).thenReturn(false);

        consumer.consume(event, null);

        // Verify Fineract sale record
        verify(fineractJournalService, times(1)).recordSale(
                eq("ORD-TEST-001"),
                eq(BigDecimal.valueOf(250000.0)),
                contains("ORD-TEST-001"),
                eq(PaymentMethod.VNPAY)
        );

        // Verify Order updated to CONFIRMED
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(1)).save(orderCaptor.capture());
        Order updatedOrder = orderCaptor.getValue();
        assertThat(updatedOrder.getCurrentStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(updatedOrder.getConfirmedAt()).isNotNull();

        // Verify Payment record saved
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(1)).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getOrder()).isEqualTo(sampleOrder);
        assertThat(savedPayment.getStatus()).isEqualTo("PAID");
        assertThat(savedPayment.getTransactionCode()).isEqualTo("TXN-ORD-001");
    }

    @Test
    @DisplayName("Idempotency: Giao dịch đã tồn tại trong DB -> Bỏ qua không hạch toán lại")
    void testConsume_Idempotency_DuplicateTransactionInDb_Skip() {
        CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                .eventType("PAYMENT_CARD_COMPLETED")
                .gateway("VNPAY")
                .orderNumber("ORD-TEST-001")
                .transactionId("TXN-DUP-DB-001")
                .isSubscription(false)
                .build();

        when(paymentRepository.existsByTransactionCode("TXN-DUP-DB-001")).thenReturn(true);

        consumer.consume(event, null);

        verify(fineractJournalService, never()).recordSale(anyString(), any(), anyString(), any());
        verify(orderRepository, never()).save(any());
        verify(userSubscriptionRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Idempotency: Giao dịch gửi liên tiếp 2 lần -> In-memory cache chặn lần thứ 2")
    void testConsume_Idempotency_DuplicateInMemory_Skip() {
        CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                .eventType("PAYMENT_CARD_COMPLETED")
                .gateway("VNPAY")
                .orderNumber("ORD-TEST-001")
                .transactionId("TXN-DUP-MEM-001")
                .grossAmount(BigDecimal.valueOf(250000.0))
                .isSubscription(false)
                .build();

        when(orderRepository.findByOrderNumber("ORD-TEST-001")).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.existsByTransactionCode("TXN-DUP-MEM-001")).thenReturn(false);

        // Lần 1: Xử lý thành công
        consumer.consume(event, null);
        verify(orderRepository, times(1)).save(any());

        // Lần 2: Bị chặn bởi in-memory cache
        consumer.consume(event, null);
        // orderRepository.save vẫn chỉ được gọi 1 lần
        verify(orderRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Idempotency: Redis lock phát hiện giao dịch trùng lặp -> Bỏ qua")
    void testConsume_Idempotency_RedisLockDuplicate_Skip() {
        consumer.setRedisTemplate(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(RedisTable.PAYMENT_CARD_DEDUP.key("TXN-REDIS-DUP-001")), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(false); // Key đã tồn tại trên Redis

        CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                .eventType("PAYMENT_CARD_COMPLETED")
                .gateway("VNPAY")
                .orderNumber("ORD-TEST-001")
                .transactionId("TXN-REDIS-DUP-001")
                .isSubscription(false)
                .build();

        consumer.consume(event, null);

        verify(fineractJournalService, never()).recordSale(anyString(), any(), anyString(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Consume từ ConsumerRecord chứa chuỗi JSON String: Parse thành công và xử lý")
    void testConsume_WithConsumerRecord_JsonString() {
        String jsonPayload = """
                {
                    "eventType": "PAYMENT_CARD_COMPLETED",
                    "gateway": "VNPAY",
                    "orderNumber": "ORD-TEST-001",
                    "transactionId": "TXN-CR-001",
                    "grossAmount": 250000.0,
                    "currency": "VND",
                    "isSubscription": false
                }
                """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("payment.card.completed", 0, 10L, "ORD-TEST-001", jsonPayload);

        when(orderRepository.findByOrderNumber("ORD-TEST-001")).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.existsByTransactionCode("TXN-CR-001")).thenReturn(false);

        consumer.consume(record, "ORD-TEST-001");

        verify(orderRepository, times(1)).save(any(Order.class));
        verify(fineractJournalService, times(1)).recordSale(eq("ORD-TEST-001"), eq(BigDecimal.valueOf(250000.0)), anyString(), eq(PaymentMethod.VNPAY));
    }

    @Test
    @DisplayName("Consume message null hoặc chuỗi rỗng: Bỏ qua an toàn không throw exception")
    void testConsume_NullOrBlank_SafeIgnore() {
        consumer.consume(null, null);
        consumer.consume("   ", null);

        verifyNoInteractions(fineractJournalService);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(userSubscriptionRepository);
    }
}
