package com.ddicg.erp.modules.order.dto.request;

import com.ddicg.erp.core.common.annotation.NormalizedId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CancelOrderRequest {

    @NormalizedId
    @NotNull(message = "Order ID không được để trống")
    private String orderId;

    @NotBlank(message = "Lý do hủy không được để trống")
    private String cancellationReason;

    public CancelOrderRequest() {}

    public CancelOrderRequest(String orderId, String cancellationReason) {
        this.orderId = orderId;
        this.cancellationReason = cancellationReason;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public static CancelOrderRequestBuilder builder() { return new CancelOrderRequestBuilder(); }

    public static class CancelOrderRequestBuilder {
        private String orderId;
        private String cancellationReason;

        CancelOrderRequestBuilder() {}

        public CancelOrderRequestBuilder orderId(String orderId) { this.orderId = orderId; return this; }
        public CancelOrderRequestBuilder cancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; return this; }
        public CancelOrderRequest build() { return new CancelOrderRequest(this.orderId, this.cancellationReason); }
    }
}
