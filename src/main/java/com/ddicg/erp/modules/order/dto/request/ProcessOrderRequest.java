package com.ddicg.erp.modules.order.dto.request;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;
@Data
public class ProcessOrderRequest {
  private String orderId; private String note; private String reason;
  private String shipperId; private String shipperName; private String shipperPhone;
  private LocalDateTime estimatedDeliveryDate; private LocalDateTime actualDeliveryDate;
  private LocalDateTime pickupDeadline; private String recipientName;
  private String condition; private Double refundAmount;
  private String orderNumber; private String transactionId; private String status;
  private String paymentMethod; private Double amount; private Map<String, Object> rawData;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getShipperId() { return shipperId; }
    public void setShipperId(String shipperId) { this.shipperId = shipperId; }
    public String getShipperName() { return shipperName; }
    public void setShipperName(String shipperName) { this.shipperName = shipperName; }
    public String getShipperPhone() { return shipperPhone; }
    public void setShipperPhone(String shipperPhone) { this.shipperPhone = shipperPhone; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

}
