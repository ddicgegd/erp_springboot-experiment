package com.ddicg.erp.core.event.consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.core.common.constants.KafkaTopics;
import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.modules.order.repository.OrderRepository;
import com.ddicg.erp.modules.order.service.OrderInventoryService;
import com.ddicg.erp.modules.order.service.OutboxOrderHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentResultConsumer.class);

    private final OrderRepository orderRepository;
    private final OrderInventoryService orderInventoryService;
    private final OutboxOrderHelper outboxOrderHelper;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics=KafkaTopics.PAYMENT_RESULT_TOPIC, groupId="payment-result-group",
            containerFactory="kafkaListenerContainerFactory",
            properties={"auto.offset.reset=earliest","enable.auto.commit=false"})
    @Transactional
    public void consume(@Payload String msg, @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int pt, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            Map<String,Object> p = objectMapper.readValue(msg, Map.class);
            String on = p.get("orderNumber")!=null?p.get("orderNumber").toString():key;
            String st = p.get("status")!=null?p.get("status").toString().toUpperCase():null;
            if (on==null||st==null) return;
            var order = orderRepository.findByOrderNumber(on).orElse(null);
            if (order==null) return;
            var cur = order.getStatus().isEmpty()?OrderStatus.PENDING:order.getStatus().get(order.getStatus().size()-1);
            if (cur!=OrderStatus.WAITING_PAYMENT&&cur!=OrderStatus.PENDING) return;
            if ("SUCCESS".equals(st)) {
                order.getStatus().add(OrderStatus.CONFIRMED);
                order.getStatus().add(OrderStatus.PROCESSING);
                order.setConfirmedAt(LocalDateTime.now());
                order.setConfirmedBy("PAYMENT");
                orderInventoryService.confirmReservation(order.getOrderItems());
                outboxOrderHelper.saveOrderStatusChangedEvent(order,OrderStatus.WAITING_PAYMENT,OrderStatus.PROCESSING,"OK","payment_gateway");
                orderRepository.save(order);
                log.info("Payment SUCCESS: {}", on);
            } else if ("FAILED".equals(st)) {
                order.getStatus().add(OrderStatus.FAILED);
                outboxOrderHelper.saveOrderStatusChangedEvent(order,OrderStatus.WAITING_PAYMENT,OrderStatus.FAILED,"FAILED","payment_gateway");
                orderRepository.save(order);
                log.info("Payment FAILED: {}", on);
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}