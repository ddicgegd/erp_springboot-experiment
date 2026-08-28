package com.ddicg.erp.core.event.service;

import com.ddicg.erp.core.event.domainevent.OutboxEnvelopeEvent;
import com.ddicg.erp.core.event.model.OutboxEvent;
import com.ddicg.erp.core.event.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxEventPublisher createPublisher() {
        return new OutboxEventPublisher(outboxEventRepository, kafkaTemplate, objectMapper);
    }

    @Test
    void handleInstantPublish_WhenPending_PublishesAndMarksSent() {
        OutboxEventPublisher publisher = createPublisher();

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId(10L)
                .eventType("ORDER_CREATED")
                .topic("order-topic")
                .messageKey("ORD-1001")
                .payload("{\"orderNumber\":\"ORD-1001\"}")
                .status("PENDING")
                .build();
        event.setId(1L);

        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        publisher.handleInstantPublish(new OutboxEnvelopeEvent(1L));

        assertEquals("SENT", event.getStatus());
        assertNotNull(event.getSentAt());
        verify(kafkaTemplate).send(any(ProducerRecord.class));
        verify(outboxEventRepository).save(event);
    }

    @Test
    void handleInstantPublish_WhenKafkaFails_MarksFailed() {
        OutboxEventPublisher publisher = createPublisher();

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId(10L)
                .eventType("ORDER_CREATED")
                .topic("order-topic")
                .messageKey("ORD-1001")
                .payload("{\"orderNumber\":\"ORD-1001\"}")
                .status("PENDING")
                .build();
        event.setId(2L);

        when(outboxEventRepository.findById(2L)).thenReturn(Optional.of(event));

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka Broker Unavailable"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture);

        publisher.handleInstantPublish(new OutboxEnvelopeEvent(2L));

        assertEquals("FAILED", event.getStatus());
        assertEquals(1, event.getRetryCount());
        assertNotNull(event.getNextRetryAt());
        verify(outboxEventRepository).save(event);
    }

    @Test
    void publishPendingEvents_PollsBackupEventsAndPublishes() {
        OutboxEventPublisher publisher = createPublisher();

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId(10L)
                .eventType("ORDER_CREATED")
                .topic("order-topic")
                .messageKey("ORD-1001")
                .payload("{\"orderNumber\":\"ORD-1001\"}")
                .status("PENDING")
                .build();
        event.setId(3L);

        when(outboxEventRepository.findEventsReadyToSend(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        publisher.publishPendingEvents();

        assertEquals("SENT", event.getStatus());
        verify(kafkaTemplate).send(any(ProducerRecord.class));
        verify(outboxEventRepository).save(event);
    }
}
