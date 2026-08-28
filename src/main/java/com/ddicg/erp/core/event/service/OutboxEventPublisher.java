package com.ddicg.erp.core.event.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.core.event.domainevent.OutboxEnvelopeEvent;
import com.ddicg.erp.core.event.model.OutboxEvent;
import com.ddicg.erp.core.event.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job và Event listener xử lý Outbox pattern - publish events đến Kafka.
 * 1. handleInstantPublish: Kích hoạt tức thì ngay khi DB transaction commit (độ trễ < 50ms).
 * 2. publishPendingEvents: Chạy định kỳ làm lưới an toàn (Safety net / Backup) để retry hoặc gửi các events bị sót.
 * 
 * @en Outbox event publisher - transactional outbox pattern with instant AFTER_COMMIT and safety net polling
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
     * Lắng nghe sự kiện OutboxEnvelopeEvent ngay sau khi DB transaction commit thành công.
     * Publish tức thì lên Kafka để đạt độ trễ sub-second (< 50ms) cho người dùng.
     * Chạy trong transaction REQUIRES_NEW độc lập để cập nhật trạng thái SENT/FAILED.
     * 
     * @en Instant event listener triggered right after DB transaction commits
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleInstantPublish(OutboxEnvelopeEvent event) {
        if (event == null || event.outboxEventId() == null) {
            return;
        }

        outboxEventRepository.findById(event.outboxEventId()).ifPresent(outboxEvent -> {
            if ("PENDING".equals(outboxEvent.getStatus())) {
                try {
                    publishEvent(outboxEvent);
                    log.debug("⚡ Instant Outbox published: id={}, topic={}, type={}", 
                            outboxEvent.getId(), outboxEvent.getTopic(), outboxEvent.getEventType());
                } catch (Exception e) {
                    log.error("Failed instant publish for event: id={}, type={}, will be retried by safety scheduler", 
                            outboxEvent.getId(), outboxEvent.getEventType(), e);
                    handlePublishFailure(outboxEvent, e.getMessage());
                }
            }
        });
    }

    /**
     * Quét và gửi events đang chờ đến Kafka (Safety Net / Backup).
     * Chỉ quét các event PENDING bị sót (tạo cách đây hơn 5 giây) hoặc FAILED cần retry.
     * 
     * @en Poll and publish pending events to Kafka (Backup & Retry safety net)
     */
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void publishPendingEvents() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pendingThreshold = now.minusSeconds(5);
        List<OutboxEvent> events = outboxEventRepository.findEventsReadyToSend(now, pendingThreshold);
        
        if (events.isEmpty()) {
            return;
        }

        log.debug("Processing {} outbox backup/retry events...", events.size());

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
            log.debug("Outbox publish completed: success={}, failed={}", successCount, failCount);
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

        Object payloadObj;
        try {
            payloadObj = objectMapper.readTree(event.getPayload());
        } catch (Exception e) {
            log.warn("Payload is not valid JSON, sending as string. id={}, type={}", event.getId(), event.getEventType());
            payloadObj = event.getPayload();
        }

        ProducerRecord<String, Object> record = new ProducerRecord<>(
                event.getTopic(),
                event.getMessageKey(),
                payloadObj
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
            
            log.debug("Event published successfully: id={}, topic={}", event.getId(), event.getTopic());
            
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
        log.debug("Starting outbox cleanup job...");
        
        // Xóa events đã gửi thành công và cũ hơn 30 ngày
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        int deleted = outboxEventRepository.deleteOldSentEvents(cutoffDate);
        
        log.debug("Outbox cleanup completed. Deleted {} old sent events.", deleted);
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