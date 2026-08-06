package com.ddicg.erp.event.producer;

import com.ddicg.erp.common.constants.KafkaTopics;
import com.ddicg.erp.service.dto.kafkaDtos.OrderEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendOrderCreatedEvent(OrderEventDto orderEventDto) {
        kafkaTemplate.send(KafkaTopics.ORDER_TOPIC, orderEventDto.getOrderId(), orderEventDto);
    }
}
