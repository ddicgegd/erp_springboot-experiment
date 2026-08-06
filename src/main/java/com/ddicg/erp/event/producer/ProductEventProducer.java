package com.ddicg.erp.event.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final String TOPIC = "product-events";

    public void publishProductCreated(Long productId) {
        publishEvent(productId, "PRODUCT_CREATED");
    }

    public void publishProductUpdated(Long productId) {
        publishEvent(productId, "PRODUCT_UPDATED");
    }

    public void publishProductDeleted(Long productId) {
        publishEvent(productId, "PRODUCT_DELETED");
    }

    private void publishEvent(Long productId, String eventType) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("productId", productId);
            payload.put("eventType", eventType);
            String message = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC, productId.toString(), message);
            log.info("Published product event {} for product {}", eventType, productId);
        } catch (Exception e) {
            log.error("Failed to publish product event", e);
        }
    }
}
