package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import org.springframework.stereotype.Component;
import com.ddicg.erp.modules.order.model.Order;

import java.util.*;

@Component
public class OrderStatusHandler {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.WAITING_PAYMENT, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.WAITING_PAYMENT, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.FAILED));
        ALLOWED.put(OrderStatus.CONFIRMED, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.PROCESSING, Set.of(OrderStatus.SHIPPING, OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.SHIPPING, Set.of(OrderStatus.DELIVERED, OrderStatus.DELAYED, OrderStatus.RETURNING));
        ALLOWED.put(OrderStatus.DELAYED, Set.of(OrderStatus.SHIPPING, OrderStatus.RETURNING));
        ALLOWED.put(OrderStatus.READY_FOR_PICKUP, Set.of(OrderStatus.DELIVERED, OrderStatus.RETURNING));
        ALLOWED.put(OrderStatus.DELIVERED, Set.of(OrderStatus.COMPLETED, OrderStatus.RETURNING));
        ALLOWED.put(OrderStatus.COMPLETED, Set.of());
        ALLOWED.put(OrderStatus.FAILED, Set.of());
        ALLOWED.put(OrderStatus.CANCELLED, Set.of());
        ALLOWED.put(OrderStatus.RETURNING, Set.of(OrderStatus.RETURNED));
        ALLOWED.put(OrderStatus.RETURNED, Set.of(OrderStatus.REFUNDED));
        ALLOWED.put(OrderStatus.REFUNDED, Set.of());
    }

    public void transitionTo(Order order, OrderStatus target, String note) {
        var current = getCurrentStatus(order);
        var allowed = ALLOWED.get(current);
        if (allowed == null || !allowed.contains(target))
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Không thể chuyển từ " + current.getDisplayName() + " sang " + target.getDisplayName());
        order.getStatus().add(target);
        order.setCurrentStatus(target);
    }

    public OrderStatus getCurrentStatus(Order order) {
        var list = order.getStatus();
        if (list == null || list.isEmpty()) return OrderStatus.PENDING;
        return list.get(list.size() - 1);
    }

    public boolean isTerminal(OrderStatus s) {
        return Set.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.FAILED, OrderStatus.REFUNDED).contains(s);
    }

    public boolean isValidTransition(OrderStatus current, OrderStatus target) {
        var allowed = ALLOWED.get(current);
        return allowed != null && allowed.contains(target);
    }
}
