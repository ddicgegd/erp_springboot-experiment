package com.ddicg.erp.modules.order.dto.request;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConfirmOrderRequest {
    private String orderId;
    private String confirmationInfo;
    private LocalDateTime confirmedAt;
    private String confirmedBy;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getConfirmationInfo() { return confirmationInfo; }
    public void setConfirmationInfo(String confirmationInfo) { this.confirmationInfo = confirmationInfo; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }

}
