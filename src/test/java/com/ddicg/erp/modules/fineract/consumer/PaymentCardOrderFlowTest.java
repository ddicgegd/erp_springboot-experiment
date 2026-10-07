package com.ddicg.erp.modules.fineract.consumer;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.core.common.model.enums.PaymentMethod;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Luồng test 4 (Task 3C - Order Flow): Kiểm thử thanh toán Order thường, Fineract recordSale & Payment record")
class PaymentCardOrderFlowTest {

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

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        consumer.setRedisTemplate(null);

        sampleOrder = Order.builder()
                .orderNumber("ORD-ECOM-001")
                .status(new ArrayList<>())
                .currentStatus(OrderStatus.PENDING)
                .totalAmount(650000.0)
                .build();
        sampleOrder.getStatus().add(OrderStatus.PENDING);
    }

    @Nested
    @DisplayName("Kiểm thử thanh toán đơn hàng qua cổng VNPAY")
    class VnPayOrderPaymentTests {

        @Test
        @DisplayName("Thanh toán Order qua VNPAY: Hạch toán recordSale Fineract, chuyển CONFIRMED (PAID), tạo Payment record")
        void testOrderPayment_VnPayGateway_Success() {
            LocalDateTime paidTime = LocalDateTime.of(2026, 10, 6, 10, 30, 0);
            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .orderNumber("ORD-ECOM-001")
                    .transactionId("TXN-VNPAY-ORD-777")
                    .cardType("VISA")
                    .cardLastFour("6789")
                    .grossAmount(BigDecimal.valueOf(650000.0))
                    .feeAmount(BigDecimal.valueOf(13000.0))
                    .netAmount(BigDecimal.valueOf(637000.0))
                    .currency("VND")
                    .paidAt(paidTime)
                    .isSubscription(false)
                    .build();

            when(orderRepository.findByOrderNumber("ORD-ECOM-001")).thenReturn(Optional.of(sampleOrder));
            when(paymentRepository.existsByTransactionCode("TXN-VNPAY-ORD-777")).thenReturn(false);

            consumer.consume(event, null);

            // 1. Kiểm tra hạch toán Fineract recordSale cho Order
            verify(fineractJournalService, times(1)).recordSale(
                    eq("ORD-ECOM-001"),
                    eq(BigDecimal.valueOf(650000.0)),
                    contains("ORD-ECOM-001"),
                    eq(PaymentMethod.VNPAY)
            );

            // 2. Kiểm tra cập nhật Order sang CONFIRMED
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository, times(1)).save(orderCaptor.capture());
            Order updatedOrder = orderCaptor.getValue();
            assertThat(updatedOrder.getCurrentStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(updatedOrder.getStatus()).contains(OrderStatus.CONFIRMED);
            assertThat(updatedOrder.getConfirmedAt()).isEqualTo(paidTime);

            // 3. Kiểm tra lưu Payment entity ghi vết thanh toán
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(1)).save(paymentCaptor.capture());
            Payment savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getOrder()).isEqualTo(sampleOrder);
            assertThat(savedPayment.getProvider()).isEqualTo("VNPAY");
            assertThat(savedPayment.getTransactionCode()).isEqualTo("TXN-VNPAY-ORD-777");
            assertThat(savedPayment.getAmount()).isEqualTo(650000.0);
            assertThat(savedPayment.getStatus()).isEqualTo("PAID");
            assertThat(savedPayment.getPaymentDate()).isEqualTo(paidTime);
            assertThat(savedPayment.getCardType()).isEqualTo("VISA");
            assertThat(savedPayment.getBankCode()).isEqualTo("****6789");
            assertThat(savedPayment.getDescription()).contains("ORD-ECOM-001");
            assertThat(savedPayment.getRawResponse()).contains("TXN-VNPAY-ORD-777");
        }
    }

    @Nested
    @DisplayName("Kiểm thử thanh toán đơn hàng qua cổng PayPal & Gateway khác")
    class OtherGatewayOrderPaymentTests {

        @Test
        @DisplayName("Thanh toán Order qua PayPal: Phân giải PaymentMethod.PAYPAL và lưu Payment provider=PAYPAL")
        void testOrderPayment_PayPalGateway_Success() {
            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("PAYPAL")
                    .orderNumber("ORD-ECOM-001")
                    .transactionId("TXN-PAYPAL-ORD-888")
                    .grossAmount(BigDecimal.valueOf(45.50))
                    .currency("USD")
                    .isSubscription(false)
                    .build();

            when(orderRepository.findByOrderNumber("ORD-ECOM-001")).thenReturn(Optional.of(sampleOrder));
            when(paymentRepository.existsByTransactionCode("TXN-PAYPAL-ORD-888")).thenReturn(false);

            consumer.consume(event, null);

            verify(fineractJournalService, times(1)).recordSale(
                    eq("ORD-ECOM-001"),
                    eq(BigDecimal.valueOf(45.50)),
                    anyString(),
                    eq(PaymentMethod.PAYPAL)
            );

            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(1)).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getProvider()).isEqualTo("PAYPAL");
        }

        @Test
        @DisplayName("Thanh toán Order qua cổng thẻ quốc tế khác: Mặc định phân giải PaymentMethod.CREDIT_CARD")
        void testOrderPayment_GenericCardGateway_ResolvesCreditCard() {
            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("STRIPE_CARD")
                    .orderNumber("ORD-ECOM-001")
                    .transactionId("TXN-STRIPE-ORD-999")
                    .grossAmount(BigDecimal.valueOf(100.0))
                    .isSubscription(false)
                    .build();

            when(orderRepository.findByOrderNumber("ORD-ECOM-001")).thenReturn(Optional.of(sampleOrder));
            when(paymentRepository.existsByTransactionCode("TXN-STRIPE-ORD-999")).thenReturn(false);

            consumer.consume(event, null);

            verify(fineractJournalService, times(1)).recordSale(
                    eq("ORD-ECOM-001"),
                    eq(BigDecimal.valueOf(100.0)),
                    anyString(),
                    eq(PaymentMethod.CREDIT_CARD)
            );
        }
    }

    @Nested
    @DisplayName("Kiểm thử Xử lý ngoại lệ, Fallback và Trạng thái Order")
    class OrderStatusAndResilienceTests {

        @Test
        @DisplayName("Đơn hàng đã ở trạng thái CONFIRMED trước đó: Không ghi đè trùng trạng thái nhưng vẫn hạch toán và lưu Payment")
        void testOrderPayment_OrderAlreadyConfirmed_MaintainsStatus() {
            sampleOrder.setCurrentStatus(OrderStatus.CONFIRMED);
            sampleOrder.getStatus().add(OrderStatus.CONFIRMED);

            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .orderNumber("ORD-ECOM-001")
                    .transactionId("TXN-ORD-ALREADY-CONFIRMED")
                    .grossAmount(BigDecimal.valueOf(650000.0))
                    .isSubscription(false)
                    .build();

            when(orderRepository.findByOrderNumber("ORD-ECOM-001")).thenReturn(Optional.of(sampleOrder));
            when(paymentRepository.existsByTransactionCode("TXN-ORD-ALREADY-CONFIRMED")).thenReturn(false);

            consumer.consume(event, null);

            // Vẫn hạch toán Fineract
            verify(fineractJournalService, times(1)).recordSale(
                    eq("ORD-ECOM-001"),
                    eq(BigDecimal.valueOf(650000.0)),
                    anyString(),
                    eq(PaymentMethod.VNPAY)
            );

            // Không gọi save Order thêm lần nữa vì đã CONFIRMED
            verify(orderRepository, never()).save(any());

            // Vẫn lưu Payment record
            verify(paymentRepository, times(1)).save(any(Payment.class));
        }

        @Test
        @DisplayName("grossAmount trong event là null: Fallback lấy totalAmount từ Order entity")
        void testOrderPayment_GrossAmountNull_FallbackToOrderTotalAmount() {
            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .orderNumber("ORD-ECOM-001")
                    .transactionId("TXN-ORD-NULL-GROSS")
                    .grossAmount(null) // null grossAmount
                    .isSubscription(false)
                    .build();

            when(orderRepository.findByOrderNumber("ORD-ECOM-001")).thenReturn(Optional.of(sampleOrder));
            when(paymentRepository.existsByTransactionCode("TXN-ORD-NULL-GROSS")).thenReturn(false);

            consumer.consume(event, null);

            verify(fineractJournalService, times(1)).recordSale(
                    eq("ORD-ECOM-001"),
                    eq(BigDecimal.valueOf(650000.0)), // Fallback từ sampleOrder.getTotalAmount()
                    anyString(),
                    eq(PaymentMethod.VNPAY)
            );
        }

        @Test
        @DisplayName("Fineract Journal Service ném Exception: Order vẫn chuyển CONFIRMED và Payment record vẫn được lưu")
        void testOrderPayment_FineractFailure_OrderAndPaymentStillPersisted() {
            CardPaymentResultEvent event = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .orderNumber("ORD-ECOM-001")
                    .transactionId("TXN-ORD-FINERACT-FAIL")
                    .grossAmount(BigDecimal.valueOf(650000.0))
                    .isSubscription(false)
                    .build();

            when(orderRepository.findByOrderNumber("ORD-ECOM-001")).thenReturn(Optional.of(sampleOrder));
            when(paymentRepository.existsByTransactionCode("TXN-ORD-FINERACT-FAIL")).thenReturn(false);

            when(fineractJournalService.recordSale(anyString(), any(), anyString(), any()))
                    .thenThrow(new RuntimeException("Fineract Connection Refused"));

            consumer.consume(event, null);

            // Order vẫn được cập nhật CONFIRMED
            verify(orderRepository, times(1)).save(sampleOrder);
            assertThat(sampleOrder.getCurrentStatus()).isEqualTo(OrderStatus.CONFIRMED);

            // Payment record vẫn được lưu
            verify(paymentRepository, times(1)).save(any(Payment.class));
        }

        @Test
        @DisplayName("OrderNumber rỗng hoặc không tìm thấy: Bỏ qua an toàn không throw exception")
        void testOrderPayment_OrderNotFoundOrEmpty_SafeIgnore() {
            CardPaymentResultEvent emptyOrderNumEvent = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .orderNumber("   ")
                    .transactionId("TXN-EMPTY-ORD")
                    .isSubscription(false)
                    .build();

            consumer.consume(emptyOrderNumEvent, null);
            verifyNoInteractions(fineractJournalService);

            CardPaymentResultEvent notFoundEvent = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .orderNumber("ORD-NON-EXISTENT")
                    .transactionId("TXN-NOT-FOUND-ORD")
                    .isSubscription(false)
                    .build();

            when(orderRepository.findByOrderNumber("ORD-NON-EXISTENT")).thenReturn(Optional.empty());

            consumer.consume(notFoundEvent, null);
            verifyNoInteractions(fineractJournalService);
        }
    }
}
