package com.ddicg.erp.core.event.repository;

import com.ddicg.erp.core.event.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository cho OutboxEvent entity.
 * Hỗ trợ các operations cho Transactional Outbox pattern.
 * 
 * @en Repository for OutboxEvent - transactional outbox pattern support
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Tìm tất cả events đang chờ gửi (PENDING status)
     * Sắp xếp theo thời gian tạo ASC để xử lý FIFO
     * Giới hạn batch size để tránh overload
     * 
     * @en Find all pending events (PENDING status)
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents();

    /**
     * Tìm events cần retry (FAILED status và đã đến thời gian retry)
     * 
     * @en Find events needing retry
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'FAILED' AND e.nextRetryAt <= :now ORDER BY e.createdAt ASC")
    List<OutboxEvent> findEventsNeedingRetry(@Param("now") LocalDateTime now);

    /**
     * Tìm tất cả events sẵn sàng gửi (PENDING + FAILED với retry time)
     * 
     * @en Find all events ready to send
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE (e.status = 'PENDING' OR (e.status = 'FAILED' AND e.nextRetryAt <= :now))
        ORDER BY e.createdAt ASC
        """)
    List<OutboxEvent> findEventsReadyToSend(@Param("now") LocalDateTime now);

    /**
     * Tìm events DEAD (đã retry quá nhiều lần)
     * Để admin xử lý thủ công hoặc cleanup
     * 
     * @en Find dead events for manual handling
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'DEAD' ORDER BY e.createdAt DESC")
    List<OutboxEvent> findDeadEvents();

    /**
     * Đếm số events đang chờ
     * 
     * @en Count pending events
     */
    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.status = 'PENDING'")
    long countPendingEvents();

    /**
     * Đếm số events thất bại
     * 
     * @en Count failed events
     */
    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.status = 'FAILED'")
    long countFailedEvents();

    /**
     * Tìm events theo aggregate (VD: tất cả events của một order)
     * 
     * @en Find events by aggregate
     */
    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, Long aggregateId);

    /**
     * Xóa events đã gửi thành công và cũ hơn X ngày
     * Cleanup job cho outbox table
     * 
     * @en Delete old sent events for cleanup
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'SENT' AND e.sentAt < :cutoffDate")
    int deleteOldSentEvents(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Kiểm tra event đã tồn tại cho aggregate (idempotency check)
     * 
     * @en Check if event already exists for aggregate
     */
    boolean existsByAggregateTypeAndAggregateIdAndEventType(String aggregateType, Long aggregateId, String eventType);
}
