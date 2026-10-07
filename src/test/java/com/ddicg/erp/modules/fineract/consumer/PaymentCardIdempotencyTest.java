package com.ddicg.erp.modules.fineract.consumer;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.model.Payment;
import com.ddicg.erp.modules.order.repository.OrderRepository;
import com.ddicg.erp.modules.order.repository.PaymentRepository;
import com.ddicg.erp.modules.payment.dto.CardPaymentResultEvent;
import com.ddicg.erp.modules.subscription.repository.SubscriptionAuditLogRepository;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
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
@DisplayName("Luồng test 2 (Task 3A - Idempotency): Kiểm thử cơ chế Idempotency chống hạch toán trùng lặp")
class PaymentCardIdempotencyTest {

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

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = Order.builder()
                .orderNumber("ORD-IDEMP-001")
                .status(new ArrayList<>())
                .currentStatus(OrderStatus.PENDING)
                .totalAmount(350000.0)
                .build();
        sampleOrder.getStatus().add(OrderStatus.PENDING);
    }

    @Nested
    @DisplayName("Kịch bản cốt lõi: Giả lập Kafka gửi cùng transactionId 3 lần liên tiếp")
    class TripleMessageDeliveryTests {

        @Test
        @DisplayName("Gửi cùng 1 transactionId 3 lần từ Kafka: Chỉ hạch toán Fineract ĐÚNG 1 LẦN DUY NHẤT")
        void testDuplicateKafkaMessages_Called3Times_FineractExecutedExactlyOnce() {
            String duplicateTxnId = "TXN-KAFKA-TRIPLE-888";
            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .orderNumber("ORD-IDEMP-001")
                    .transactionId(duplicateTxnId)
                    .grossAmount(BigDecimal.valueOf(350000.0))
                    .currency("VND")
                    .paidAt(LocalDateTime.now())
                    .isSubscription(false)
                    .build();

            when(orderRepository.findByOrderNumber("ORD-IDEMP-001")).thenReturn(Optional.of(sampleOrder));
            when(paymentRepository.existsByTransactionCode(duplicateTxnId)).thenReturn(false);

            // Gửi lần 1: Message đầu tiên đến từ Kafka
            consumer.consume(event, null);

            // Gửi lần 2: Message trùng lặp thứ hai đến từ Kafka (vd: Kafka retry hoặc rebalance)
            consumer.consume(event, null);

            // Gửi lần 3: Message trùng lặp thứ ba đến từ Kafka
            consumer.consume(event, null);

            // Xác minh Fineract journal recordSale CHỈ ĐƯỢC GỌI ĐÚNG 1 LẦN DUY NHẤT
            verify(fineractJournalService, times(1)).recordSale(
                    eq("ORD-IDEMP-001"),
                    eq(BigDecimal.valueOf(350000.0)),
                    contains("ORD-IDEMP-001"),
                    eq(PaymentMethod.VNPAY)
            );

            // Xác minh cập nhật Order chỉ được lưu đúng 1 lần
            verify(orderRepository, times(1)).save(sampleOrder);

            // Xác minh Payment record chỉ được lưu đúng 1 lần
            verify(paymentRepository, times(1)).save(any(Payment.class));
        }
    }

    @Nested
    @DisplayName("Kiểm thử tầng In-Memory Deduplication (Fast check chống race-condition)")
    class InMemoryDeduplicationTests {

        @Test
        @DisplayName("In-Memory cache chặn đứng giao dịch trùng lặp trong cùng tiến trình")
        void testInMemoryCache_DuplicateTransactionBlocked() {
            String txnId = "TXN-INMEM-999";
            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .orderNumber("ORD-IDEMP-001")
                    .transactionId(txnId)
                    .grossAmount(BigDecimal.valueOf(350000.0))
                    .isSubscription(false)
                    .build();

            when(orderRepository.findByOrderNumber("ORD-IDEMP-001")).thenReturn(Optional.of(sampleOrder));
            when(paymentRepository.existsByTransactionCode(txnId)).thenReturn(false);

            // Lần 1: Thành công
            consumer.consume(event, null);
            verify(fineractJournalService, times(1)).recordSale(anyString(), any(), anyString(), any());

            // Lần 2: Bị chặn bởi in-memory processedTransactions Set
            consumer.consume(event, null);
            verify(fineractJournalService, times(1)).recordSale(anyString(), any(), anyString(), any());
            verify(orderRepository, times(1)).save(any());
        }
    }

    @Nested
    @DisplayName("Kiểm thử tầng Redis Distributed Lock Deduplication")
    class RedisDeduplicationTests {

        @Test
        @DisplayName("Redis lock trả về false (key đã tồn tại) -> Bỏ qua lập tức, không gọi DB hay Fineract")
        void testRedisLock_DuplicateKeyBlocked() {
            consumer.setRedisTemplate(redisTemplate);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String txnId = "TXN-REDIS-EXISTS-123";
            String redisKey = RedisTable.PAYMENT_CARD_DEDUP.key(txnId);

            // Giả lập Redis báo key đã có do pod khác đang/đã xử lý
            when(valueOperations.setIfAbsent(eq(redisKey), eq("PROCESSING"), eq(Duration.ofDays(7))))
                    .thenReturn(false);

            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .orderNumber("ORD-IDEMP-001")
                    .transactionId(txnId)
                    .grossAmount(BigDecimal.valueOf(350000.0))
                    .isSubscription(false)
                    .build();

            consumer.consume(event, null);

            // Tuyệt đối không hạch toán Fineract
            verifyNoInteractions(fineractJournalService);
            // Không tương tác với order/payment repository
            verify(orderRepository, never()).save(any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Redis thành công: Set lock PROCESSING khi bắt đầu và PROCESSED khi kết thúc")
        void testRedisLock_Lifecycle_Success() {
            consumer.setRedisTemplate(redisTemplate);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String txnId = "TXN-REDIS-SUCCESS-456";
            String redisKey = RedisTable.PAYMENT_CARD_DEDUP.key(txnId);

            when(valueOperations.setIfAbsent(eq(redisKey), eq("PROCESSING"), eq(Duration.ofDays(7))))
                    .thenReturn(true);
            when(orderRepository.findByOrderNumber("ORD-IDEMP-001")).thenReturn(Optional.of(sampleOrder));
            when(paymentRepository.existsByTransactionCode(txnId)).thenReturn(false);

            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .orderNumber("ORD-IDEMP-001")
                    .transactionId(txnId)
                    .grossAmount(BigDecimal.valueOf(350000.0))
                    .isSubscription(false)
                    .build();

            consumer.consume(event, null);

            // Kiểm tra trạng thái cuối cùng được set là PROCESSED với TTL 30 ngày
            verify(valueOperations, times(1)).set(eq(redisKey), eq("PROCESSED"), eq(Duration.ofDays(30)));
        }
    }

    @Nested
    @DisplayName("Kiểm thử tầng Database Deduplication (Payment record check)")
    class DatabaseDeduplicationTests {

        @Test
        @DisplayName("DB Payment đã chứa transactionCode -> Bỏ qua hạch toán")
        void testDatabaseCheck_DuplicateTransactionInDb_Skip() {
            String txnId = "TXN-IN-DB-ALREADY";
            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .orderNumber("ORD-IDEMP-001")
                    .transactionId(txnId)
                    .grossAmount(BigDecimal.valueOf(350000.0))
                    .isSubscription(false)
                    .build();

            when(paymentRepository.existsByTransactionCode(txnId)).thenReturn(true);

            consumer.consume(event, null);

            verify(fineractJournalService, never()).recordSale(anyString(), any(), anyString(), any());
            verify(orderRepository, never()).save(any());
            verify(paymentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Kiểm thử Khả năng chịu lỗi và Phục hồi (Fault Tolerance & Retry)")
    class FaultToleranceTests {

        @Test
        @DisplayName("Redis bị lỗi kết nối: Tự động fallback sang kiểm tra DB an toàn và xử lý thành công")
        void testRedisFailure_FallbackToDatabase_Success() {
            consumer.setRedisTemplate(redisTemplate);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String txnId = "TXN-REDIS-FAIL-FALLBACK";
            String redisKey = RedisTable.PAYMENT_CARD_DEDUP.key(txnId);

            // Giả lập Redis bị sập / timeout
            when(valueOperations.setIfAbsent(eq(redisKey), eq("PROCESSING"), eq(Duration.ofDays(7))))
                    .thenThrow(new RedisConnectionFailureException("Redis connection timed out"));

            when(orderRepository.findByOrderNumber("ORD-IDEMP-001")).thenReturn(Optional.of(sampleOrder));
            when(paymentRepository.existsByTransactionCode(txnId)).thenReturn(false);

            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .orderNumber("ORD-IDEMP-001")
                    .transactionId(txnId)
                    .grossAmount(BigDecimal.valueOf(350000.0))
                    .isSubscription(false)
                    .build();

            consumer.consume(event, null);

            // Hệ thống không crash, vẫn hoàn tất hạch toán Fineract
            verify(fineractJournalService, times(1)).recordSale(
                    eq("ORD-IDEMP-001"),
                    eq(BigDecimal.valueOf(350000.0)),
                    anyString(),
                    eq(PaymentMethod.VNPAY)
            );
            verify(orderRepository, times(1)).save(sampleOrder);
        }

        @Test
        @DisplayName("Khi xử lý bị lỗi: Giải phóng lock In-Memory và Redis để Kafka có thể retry")
        void testProcessingFailure_ReleasesLockForRetry() {
            consumer.setRedisTemplate(redisTemplate);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String txnId = "TXN-FAIL-RETRY-001";
            String redisKey = RedisTable.PAYMENT_CARD_DEDUP.key(txnId);

            when(valueOperations.setIfAbsent(eq(redisKey), eq("PROCESSING"), eq(Duration.ofDays(7))))
                    .thenReturn(true);
            when(orderRepository.findByOrderNumber("ORD-IDEMP-001")).thenReturn(Optional.of(sampleOrder));
            when(paymentRepository.existsByTransactionCode(txnId)).thenReturn(false);

            // Giả lập lưu Order bị lỗi cơ sở dữ liệu
            when(orderRepository.save(any())).thenThrow(new RuntimeException("DB Connection Deadlock"));

            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .orderNumber("ORD-IDEMP-001")
                    .transactionId(txnId)
                    .grossAmount(BigDecimal.valueOf(350000.0))
                    .isSubscription(false)
                    .build();

            consumer.consume(event, null);

            // Xác nhận Redis key lock đã được delete để giải phóng cho lần retry kế tiếp
            verify(redisTemplate, times(1)).delete(eq(redisKey));
        }
    }
}
