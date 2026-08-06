package com.ddicg.erp.service;

import com.ddicg.erp.model.entity.OutboxEvent;
import com.ddicg.erp.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job xử lý Outbox pattern - poll và gửi events đến Kafka.
 * Chạy mỗi 5 giây để đảm bảo events được gửi nhanh nhất có thể.
 * 
 * @en Outbox event publisher - scheduled job for transactional outbox pattern
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY = 3;

    /**
     * Poll và gửi events đang chờ đến Kafka.
     * Chạy mỗi 30 giây (giảm tần suất để tránh query DB quá nhiều).
     * 
     * @en Poll and publish pending events to Kafka
     */
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findEventsReadyToSend(LocalDateTime.now());
        
        if (events.isEmpty()) {
            return;
        }

        log.info("Processing {} outbox events...", events.size());

        int successCount = 0;
        int failCount = 0;

        for (OutboxEvent event : events) {
            try {
                publishEvent(event);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to publish event: id={}, type={}", event.getId(), event.getEventType(), e);
                handlePublishFailure(event, e.getMessage());
                failCount++;
            }
        }

        if (successCount > 0 || failCount > 0) {
            log.info("Outbox publish completed: success={}, failed={}", successCount, failCount);
        }
    }

    /**
     * Gửi event đến Kafka.
     * 
     * @en Publish single event to Kafka
     */
    private void publishEvent(OutboxEvent event) {
        log.debug("Publishing event: id={}, topic={}, type={}", 
                event.getId(), event.getTopic(), event.getEventType());

        ProducerRecord<String, Object> record = new ProducerRecord<>(
                event.getTopic(),
                event.getMessageKey(),
                event.getPayload()
        );

        // Add headers for tracing
        record.headers().add("event_id", String.valueOf(event.getId()).getBytes());
        record.headers().add("event_type", event.getEventType().getBytes());
        record.headers().add("correlation_id", 
                event.getCorrelationId() != null ? event.getCorrelationId().getBytes() : new byte[0]);

        try {
            // Send synchronously và wait for acknowledgment
            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
            
            // Mark as sent
            event.markAsSent();
            outboxEventRepository.save(event);
            
            log.info("Event published successfully: id={}, topic={}", event.getId(), event.getTopic());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to send event to Kafka", e);
        }
    }

    /**
     * Xử lý khi publish thất bại.
     * 
     * @en Handle publish failure
     */
    private void handlePublishFailure(OutboxEvent event, String errorMessage) {
        event.markAsFailed(errorMessage);
        outboxEventRepository.save(event);
        
        // Alert if event is dead
        if ("DEAD".equals(event.getStatus())) {
            log.error("🚨 EVENT DEAD after {} retries: id={}, type={}, aggregate={}:{}", 
                    event.getRetryCount(),
                    event.getId(), 
                    event.getEventType(),
                    event.getAggregateType(),
                    event.getAggregateId());
        }
    }

    /**
     * Cleanup old sent events.
     * Chạy mỗi ngày lúc 3:00 AM.
     * 
     * @en Cleanup old sent events - runs daily at 3 AM
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldEvents() {
        log.info("Starting outbox cleanup job...");
        
        // Xóa events đã gửi thành công và cũ hơn 30 ngày
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        int deleted = outboxEventRepository.deleteOldSentEvents(cutoffDate);
        
        log.info("Outbox cleanup completed. Deleted {} old sent events.", deleted);
    }

    /**
     * Alert về các events đang chờ.
     * Chạy mỗi 10 phút.
     * 
     * @en Alert about pending events - runs every 10 minutes
     */
    @Scheduled(fixedDelay = 600000) // 10 minutes
    public void alertPendingEvents() {
        long pendingCount = outboxEventRepository.countPendingEvents();
        long failedCount = outboxEventRepository.countFailedEvents();
        
        if (pendingCount > 1000) {
            log.warn("⚠️ HIGH OUTBOX BACKLOG: {} pending events", pendingCount);
        }
        
        if (failedCount > 100) {
            log.error("🚨 OUTBOX FAILURES: {} failed events need attention", failedCount);
        }
    }
}
