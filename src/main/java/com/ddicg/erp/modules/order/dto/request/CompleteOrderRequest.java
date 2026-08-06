package com.ddicg.erp.modules.order.dto.request;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CompleteOrderRequest {
    private String orderId;
    private String completionInfo;
    private LocalDateTime completedAt;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCompletionInfo() { return completionInfo; }
    public void setCompletionInfo(String completionInfo) { this.completionInfo = completionInfo; }

}
