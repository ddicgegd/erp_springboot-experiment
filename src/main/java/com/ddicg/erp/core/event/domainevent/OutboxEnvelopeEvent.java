package com.ddicg.erp.core.event.domainevent;

/**
 * Spring Application Event nội bộ đại diện cho OutboxEvent vừa được lưu vào DB.
 * Sử dụng để kích hoạt publish tức thì qua @TransactionalEventListener(AFTER_COMMIT).
 */
public record OutboxEnvelopeEvent(Long outboxEventId) {
}
