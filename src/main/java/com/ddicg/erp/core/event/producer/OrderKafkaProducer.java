package com.ddicg.erp.core.event.producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.core.common.constants.KafkaTopics;
import com.ddicg.erp.modules.order.dto.kafka.OrderEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderKafkaProducer {
    private static final Logger log = LoggerFactory.getLogger(OrderKafkaProducer.class);


    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendOrderCreatedEvent(OrderEventDto orderEventDto) {
        kafkaTemplate.send(KafkaTopics.ORDER_TOPIC, orderEventDto.getOrderId(), orderEventDto);
    }
}