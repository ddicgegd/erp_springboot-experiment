package com.ddicg.erp.service.dto.request;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class UpdateDeliveryRequest {
    private String orderId;
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;
    private String deliveryInfo;
}
