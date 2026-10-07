package com.ddicg.erp.modules.payment.dto;

import com.ddicg.erp.core.common.constants.KafkaTopics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Luồng test 1 (Task 1 & 2): Unit test Topic Constants & CardPaymentResultEvent DTO")
class CardPaymentResultEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("Kiểm thử tính toàn vẹn của các Topic Constants trong KafkaTopics")
    class KafkaTopicsIntegrityTests {

        @Test
        @DisplayName("Xác nhận các hằng số Topic thẻ và hệ thống trong KafkaTopics chính xác")
        void testKafkaTopicsConstants() {
            // Task 1: Các hằng số thanh toán thẻ
            assertThat(KafkaTopics.PAYMENT_CARD_COMPLETED)
                    .isEqualTo("payment.card.completed");
            assertThat(KafkaTopics.PAYMENT_CARD_REQUEST)
                    .isEqualTo("payment.card.request");
            assertThat(KafkaTopics.PAYMENT_CARD_RECURRING)
                    .isEqualTo("payment.card.recurring.charge");

            // Các topic cốt lõi khác
            assertThat(KafkaTopics.ORDER_TOPIC)
                    .isEqualTo("order-topic");
            assertThat(KafkaTopics.NOTIFICATION_EMAIL_TOPIC)
                    .isEqualTo("notification-email-topic");
            assertThat(KafkaTopics.NOTIFICATION_EMAIL_DLT)
                    .isEqualTo("notification-email-topic.DLT");
        }
    }

    @Nested
    @DisplayName("Kiểm thử Deserialization DTO từ JSON mẫu của cổng thanh toán VNPAY & PayPal")
    class DtoDeserializationTests {

        @Test
        @DisplayName("Deserialize JSON mẫu VNPAY (Order thường, VND, kèm các trường bổ sung của VNPAY)")
        void testDeserialize_VnPayJsonSample() throws JsonProcessingException {
            String vnpayJson = """
                    {
                        "eventType": "PAYMENT_CARD_COMPLETED",
                        "gateway": "VNPAY",
                        "orderNumber": "ORD-VNPAY-2026-001",
                        "transactionId": "VNPAY_TXN_998877",
                        "cardType": "VISA",
                        "cardLastFour": "8888",
                        "grossAmount": 500000.00,
                        "feeAmount": 11000.00,
                        "netAmount": 489000.00,
                        "currency": "VND",
                        "paidAt": "2026-10-06T10:15:30",
                        "subscriptionId": null,
                        "isSubscription": false,
                        "vnp_ResponseCode": "00",
                        "vnp_BankCode": "NCB",
                        "vnp_CardHolder": "NGUYEN VAN A"
                    }
                    """;

            CardPaymentResultEvent event = objectMapper.readValue(vnpayJson, CardPaymentResultEvent.class);

            assertThat(event).isNotNull();
            assertThat(event.getEventType()).isEqualTo("PAYMENT_CARD_COMPLETED");
            assertThat(event.getGateway()).isEqualTo("VNPAY");
            assertThat(event.getOrderNumber()).isEqualTo("ORD-VNPAY-2026-001");
            assertThat(event.getTransactionId()).isEqualTo("VNPAY_TXN_998877");
            assertThat(event.getCardType()).isEqualTo("VISA");
            assertThat(event.getCardLastFour()).isEqualTo("8888");
            assertThat(event.getGrossAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
            assertThat(event.getFeeAmount()).isEqualByComparingTo(new BigDecimal("11000.00"));
            assertThat(event.getNetAmount()).isEqualByComparingTo(new BigDecimal("489000.00"));
            assertThat(event.getCurrency()).isEqualTo("VND");
            assertThat(event.getPaidAt()).isEqualTo(LocalDateTime.of(2026, 10, 6, 10, 15, 30));
            assertThat(event.getSubscriptionId()).isNull();
            assertThat(event.getIsSubscription()).isFalse();
            assertThat(event.isSubscription()).isFalse();
        }

        @Test
        @DisplayName("Deserialize JSON mẫu PayPal (Subscription gói cước, USD, kèm các trường bổ sung của PayPal)")
        void testDeserialize_PayPalJsonSample() throws JsonProcessingException {
            String paypalJson = """
                    {
                        "eventType": "PAYMENT_CARD_COMPLETED",
                        "gateway": "PAYPAL",
                        "orderNumber": null,
                        "transactionId": "PAYPAL_TXN_CAPTURE_554433",
                        "cardType": "MASTERCARD",
                        "cardLastFour": "1234",
                        "grossAmount": 19.99,
                        "feeAmount": 0.88,
                        "netAmount": 19.11,
                        "currency": "USD",
                        "paidAt": "2026-10-06T14:30:00",
                        "subscriptionId": 101,
                        "isSubscription": true,
                        "paypal_payer_id": "PAYER_987",
                        "paypal_capture_id": "CAP_112233",
                        "paypal_protection_eligibility": "ELIGIBLE"
                    }
                    """;

            CardPaymentResultEvent event = objectMapper.readValue(paypalJson, CardPaymentResultEvent.class);

            assertThat(event).isNotNull();
            assertThat(event.getEventType()).isEqualTo("PAYMENT_CARD_COMPLETED");
            assertThat(event.getGateway()).isEqualTo("PAYPAL");
            assertThat(event.getOrderNumber()).isNull();
            assertThat(event.getTransactionId()).isEqualTo("PAYPAL_TXN_CAPTURE_554433");
            assertThat(event.getCardType()).isEqualTo("MASTERCARD");
            assertThat(event.getCardLastFour()).isEqualTo("1234");
            assertThat(event.getGrossAmount()).isEqualByComparingTo(new BigDecimal("19.99"));
            assertThat(event.getFeeAmount()).isEqualByComparingTo(new BigDecimal("0.88"));
            assertThat(event.getNetAmount()).isEqualByComparingTo(new BigDecimal("19.11"));
            assertThat(event.getCurrency()).isEqualTo("USD");
            assertThat(event.getPaidAt()).isEqualTo(LocalDateTime.of(2026, 10, 6, 14, 30, 0));
            assertThat(event.getSubscriptionId()).isEqualTo(101L);
            assertThat(event.getIsSubscription()).isTrue();
            assertThat(event.isSubscription()).isTrue();
        }
    }

    @Nested
    @DisplayName("Kiểm thử Round-trip Serialization / Deserialization và thuộc tính 13 trường DTO")
    class DtoRoundTripTests {

        @Test
        @DisplayName("Round-trip serialization & deserialization đầy đủ 13 trường")
        void testRoundTripSerialization() throws JsonProcessingException {
            LocalDateTime paidTime = LocalDateTime.of(2026, 10, 6, 11, 22, 33);
            CardPaymentResultEvent original = CardPaymentResultEvent.builder()
                    .eventType("PAYMENT_CARD_COMPLETED")
                    .gateway("VNPAY")
                    .orderNumber("ORD-ROUND-TRIP-999")
                    .transactionId("TXN-RT-12345")
                    .cardType("JCB")
                    .cardLastFour("9999")
                    .grossAmount(BigDecimal.valueOf(1250000))
                    .feeAmount(BigDecimal.valueOf(25000))
                    .netAmount(BigDecimal.valueOf(1225000))
                    .currency("VND")
                    .paidAt(paidTime)
                    .subscriptionId(777L)
                    .isSubscription(true)
                    .build();

            String json = objectMapper.writeValueAsString(original);
            assertThat(json).contains("\"isSubscription\":true");
            assertThat(json).contains("\"transactionId\":\"TXN-RT-12345\"");

            CardPaymentResultEvent deserialized = objectMapper.readValue(json, CardPaymentResultEvent.class);

            assertThat(deserialized.getEventType()).isEqualTo(original.getEventType());
            assertThat(deserialized.getGateway()).isEqualTo(original.getGateway());
            assertThat(deserialized.getOrderNumber()).isEqualTo(original.getOrderNumber());
            assertThat(deserialized.getTransactionId()).isEqualTo(original.getTransactionId());
            assertThat(deserialized.getCardType()).isEqualTo(original.getCardType());
            assertThat(deserialized.getCardLastFour()).isEqualTo(original.getCardLastFour());
            assertThat(deserialized.getGrossAmount()).isEqualByComparingTo(original.getGrossAmount());
            assertThat(deserialized.getFeeAmount()).isEqualByComparingTo(original.getFeeAmount());
            assertThat(deserialized.getNetAmount()).isEqualByComparingTo(original.getNetAmount());
            assertThat(deserialized.getCurrency()).isEqualTo(original.getCurrency());
            assertThat(deserialized.getPaidAt()).isEqualTo(original.getPaidAt());
            assertThat(deserialized.getSubscriptionId()).isEqualTo(original.getSubscriptionId());
            assertThat(deserialized.getIsSubscription()).isEqualTo(original.getIsSubscription());
            assertThat(deserialized.isSubscription()).isTrue();
        }

        @Test
        @DisplayName("Hành vi của isSubscription() khi isSubscription là null")
        void testIsSubscriptionHelper_WhenNull() {
            CardPaymentResultEvent event = new CardPaymentResultEvent();
            event.setIsSubscription(null);
            assertThat(event.isSubscription()).isFalse();

            event.setIsSubscription(false);
            assertThat(event.isSubscription()).isFalse();

            event.setIsSubscription(true);
            assertThat(event.isSubscription()).isTrue();
        }
    }
}
