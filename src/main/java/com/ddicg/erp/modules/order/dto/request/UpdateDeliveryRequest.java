package com.ddicg.erp.modules.order.dto.request;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class UpdateDeliveryRequest {
    private String orderId;
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;
    private String deliveryInfo;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getDeliveryInfo() { return deliveryInfo; }
    public void setDeliveryInfo(String deliveryInfo) { this.deliveryInfo = deliveryInfo; }


    public java.time.LocalDateTime getEstimatedDeliveryDate() { return null; }
    public java.time.LocalDateTime getActualDeliveryDate() { return null; }
}
