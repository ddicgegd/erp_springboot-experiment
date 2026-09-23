package com.ddicg.erp.modules.fineract.consumer;

import com.ddicg.erp.core.common.constants.KafkaTopics;
import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Consumer lắng nghe sự kiện đơn hàng từ order-topic để đồng bộ hạch toán sổ cái sang Apache Fineract.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.fineract.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class OrderFineractConsumer {

    private final FineractJournalService fineractJournalService;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.ORDER_TOPIC,
            groupId = "fineract-order-group",
            containerFactory = "kafkaListenerContainerFactory",
            properties = {"auto.offset.reset=earliest", "enable.auto.commit=false"})
    public void consume(Object msg,
                        @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        try {
            if (msg instanceof ConsumerRecord<?, ?> record) {
                if (key == null && record.key() != null) {
                    key = String.valueOf(record.key());
                }
                msg = record.value();
            }

            if (msg == null) {
                return;
            }

            JsonNode root;
            if (msg instanceof JsonNode jsonNode) {
                root = jsonNode;
            } else if (msg instanceof String str) {
                if (str.isBlank()) {
                    return;
                }
                root = objectMapper.readTree(str);
            } else if (msg instanceof byte[] bytes) {
                if (bytes.length == 0) {
                    return;
                }
                root = objectMapper.readTree(bytes);
            } else {
                root = objectMapper.valueToTree(msg);
            }

            String eventType = root.path("eventType").asText(null);
            String orderNumber = root.path("orderNumber").asText(root.path("orderId").asText(key));

            if (eventType == null || orderNumber == null) {
                return;
            }

            if ("ORDER_STATUS_CHANGED".equals(eventType)) {
                String newStatus = root.path("newStatus").asText("");

                if ("PROCESSING".equalsIgnoreCase(newStatus)) {
                    processSaleJournal(orderNumber);
                } else if ("REFUNDED".equalsIgnoreCase(newStatus)) {
                    processRefundJournal(orderNumber);
                }
            } else if ("ORDER_CREATED".equals(eventType)) {
                String initialStatus = root.path("initialStatus").asText("");
                if ("PROCESSING".equalsIgnoreCase(initialStatus)) {
                    processSaleJournal(orderNumber);
                }
            }
        } catch (Exception e) {
            log.error("⚠️ OrderFineractConsumer error processing message: {}", msg, e);
        }
    }

    private void processSaleJournal(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber).orElse(null);
        if (order == null || order.getTotalAmount() == null || order.getTotalAmount() <= 0) {
            log.warn("⚠️ Order not found or invalid amount for Fineract sale entry: {}", orderNumber);
            return;
        }

        try {
            BigDecimal amount = BigDecimal.valueOf(order.getTotalAmount());
            fineractJournalService.recordSale(orderNumber, amount, "Doanh thu bán hàng từ đơn: " + orderNumber);
            log.debug("📊 Fineract journal recorded for SALE: order={}, amount={}", orderNumber, amount);
        } catch (Exception e) {
            log.error("❌ Failed to record Fineract SALE journal for order: {}", orderNumber, e);
        }
    }

    private void processRefundJournal(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber).orElse(null);
        if (order == null || order.getTotalAmount() == null || order.getTotalAmount() <= 0) {
            log.warn("⚠️ Order not found or invalid amount for Fineract refund entry: {}", orderNumber);
            return;
        }

        try {
            BigDecimal amount = BigDecimal.valueOf(order.getTotalAmount());
            fineractJournalService.recordRefund(orderNumber, amount, "Hoàn tiền trả hàng cho đơn: " + orderNumber);
            log.debug("📊 Fineract journal recorded for REFUND: order={}, amount={}", orderNumber, amount);
        } catch (Exception e) {
            log.error("❌ Failed to record Fineract REFUND journal for order: {}", orderNumber, e);
        }
    }
}
