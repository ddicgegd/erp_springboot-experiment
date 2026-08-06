package com.ddicg.erp.service.dto.request;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;
@Data
public class PaymentCallbackRequest {
  private String orderId; private String note; private String reason;
  private String shipperId; private String shipperName; private String shipperPhone;
  private LocalDateTime estimatedDeliveryDate; private LocalDateTime actualDeliveryDate;
  private LocalDateTime pickupDeadline; private String recipientName;
  private String condition; private Double refundAmount;
  private String orderNumber; private String transactionId; private String status;
  private String paymentMethod; private Double amount; private Map<String, Object> rawData;
}
