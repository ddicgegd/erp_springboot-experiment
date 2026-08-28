package com.ddicg.erp.modules.order.event;

/**
 * Domain Event phát ra khi trạng thái đơn hàng thay đổi.
 */
public record OrderStatusChangedEvent(
        String eventType,
        String orderNumber,
        String previousStatus,
        String newStatus,
        String previousStatusDescription,
        String newStatusDescription,
        String note,
        String changedAt,
        String changedBy,
        String changedByRole,
        String correlationId
) {}
