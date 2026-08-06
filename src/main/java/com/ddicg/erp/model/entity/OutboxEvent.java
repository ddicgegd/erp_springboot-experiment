package com.ddicg.erp.model.entity;

import com.ddicg.erp.model.base.IdentityOnly;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Entity lưu trữ Kafka events trong cùng database transaction với business data.
 * Scheduled job sẽ poll và gửi các events này đến Kafka.
 * 
 * @en Outbox pattern entity for transactional event publishing
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id"),
        @Index(name = "idx_outbox_status", columnList = "sent_at"),
        @Index(name = "idx_outbox_event_type", columnList = "event_type"),
        @Index(name = "idx_outbox_created_at", columnList = "created_at")
})
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutboxEvent extends IdentityOnly<Long> {

    /**
     * Loại aggregate (VD: ORDER, PAYMENT, PRODUCT)
     * @en Aggregate type
     */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    String aggregateType;

    /**
     * ID của aggregate (VD: order ID)
     * @en Aggregate ID
     */
    @Column(name = "aggregate_id", nullable = false)
    Long aggregateId;

    /**
     * Tên event (VD: ORDER_CREATED, ORDER_CONFIRMED)
     * @en Event type name
     */
    @Column(name = "event_type", nullable = false, length = 100)
    String eventType;

    /**
     * Kafka topic để gửi event
     * @en Target Kafka topic
     */
    @Column(name = "topic", nullable = false, length = 255)
    String topic;

    /**
     * Message key cho Kafka partition
     * @en Kafka message key
     */
    @Column(name = "message_key", length = 255)
    String messageKey;

    /**
     * Payload JSON của event
     * @en Event payload as JSON
     */
    @Column(name = "payload", nullable = false, columnDefinition = "CLOB")
    String payload;

    /**
     * Correlation ID để trace event across services
     * @en Correlation ID for distributed tracing
     */
    @Column(name = "correlation_id", length = 100)
    String correlationId;

    /**
     * Số lần retry đã thử
     * @en Number of retry attempts
     */
    @Column(name = "retry_count")
    @Builder.Default
    Integer retryCount = 0;

    /**
     * Thời gian tạo event
     * @en Event creation timestamp
     */
    @Column(name = "created_at", nullable = false)
    LocalDateTime createdAt;

    /**
     * Thời gian gửi thành công (null = chưa gửi)
     * @en Sent timestamp (null = not yet sent)
     */
    @Column(name = "sent_at")
    LocalDateTime sentAt;

    /**
     * Thời gian retry tiếp theo
     * @en Next retry timestamp
     */
    @Column(name = "next_retry_at")
    LocalDateTime nextRetryAt;

    /**
     * Lỗi cuối cùng (nếu có)
     * @en Last error message
     */
    @Column(name = "last_error", columnDefinition = "CLOB")
    String lastError;

    /**
     * Trạng thái: PENDING, SENT, FAILED, DEAD
     * @en Status: PENDING, SENT, FAILED, DEAD
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    String status = "PENDING";

    /**
     * Đánh dấu đã gửi thành công
     * @en Mark as sent
     */
    public void markAsSent() {
        this.sentAt = LocalDateTime.now();
        this.status = "SENT";
        this.lastError = null;
    }

    /**
     * Đánh dấu thất bại và lên lịch retry
     * @en Mark as failed and schedule retry
     */
    public void markAsFailed(String errorMessage) {
        this.retryCount++;
        this.lastError = errorMessage;
        this.status = "FAILED";
        
        // Exponential backoff: 1min, 5min, 15min, 1h, 6h, 24h
        long[] backoffMinutes = {1, 5, 15, 60, 360, 1440};
        int index = Math.min(this.retryCount - 1, backoffMinutes.length - 1);
        this.nextRetryAt = LocalDateTime.now().plusMinutes(backoffMinutes[index]);
        
        // Đánh dấu DEAD sau 6 lần thử
        if (this.retryCount >= 6) {
            this.status = "DEAD";
        }
    }

    /**
     * Kiểm tra có cần retry không
     * @en Check if event needs retry
     */
    public boolean needsRetry() {
        return "FAILED".equals(this.status) 
                && this.nextRetryAt != null 
                && LocalDateTime.now().isAfter(this.nextRetryAt);
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = "PENDING";
        }
    }
}
