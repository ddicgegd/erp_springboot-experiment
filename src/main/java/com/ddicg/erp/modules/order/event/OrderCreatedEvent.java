package com.ddicg.erp.modules.order.event;

import java.util.List;

/**
 * Domain Event phát ra khi một đơn hàng mới được tạo thành công.
 */
public record OrderCreatedEvent(
        String eventType,
        String orderNumber,
        Double amount,
        String currency,
        String paymentMethod,
        String bankCode,
        String initialStatus,
        Long customerId,
        List<String> roles,
        String ipAddress,
        String language,
        String createdAt,
        String correlationId
) {}
