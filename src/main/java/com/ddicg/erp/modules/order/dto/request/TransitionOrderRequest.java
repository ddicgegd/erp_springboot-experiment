package com.ddicg.erp.modules.order.dto.request;

import com.ddicg.erp.core.common.annotation.NormalizedId;
import com.ddicg.erp.core.common.model.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public class TransitionOrderRequest {
    @NormalizedId
    @NotNull(message = "Order ID không được để trống")
    private String orderId;

    @NotNull(message = "Trạng thái đích không được để trống")
    private OrderStatus targetStatus;

    private String shipperId;
    private String note;

    public TransitionOrderRequest() {}

    public TransitionOrderRequest(String orderId, OrderStatus targetStatus, String shipperId, String note) {
        this.orderId = orderId;
        this.targetStatus = targetStatus;
        this.shipperId = shipperId;
        this.note = note;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public OrderStatus getTargetStatus() { return targetStatus; }
    public void setTargetStatus(OrderStatus targetStatus) { this.targetStatus = targetStatus; }
    public String getShipperId() { return shipperId; }
    public void setShipperId(String shipperId) { this.shipperId = shipperId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
