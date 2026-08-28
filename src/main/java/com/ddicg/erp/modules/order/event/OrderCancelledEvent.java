package com.ddicg.erp.modules.order.event;

/**
 * Domain Event phát ra khi đơn hàng bị hủy.
 */
public record OrderCancelledEvent(
        String eventType,
        String orderNumber,
        String reason,
        boolean refundRequired,
        String cancelledAt,
        String cancelledBy,
        String correlationId
) {}
