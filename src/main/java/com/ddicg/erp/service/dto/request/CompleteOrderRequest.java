package com.ddicg.erp.service.dto.request;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CompleteOrderRequest {
    private String orderId;
    private String completionInfo;
    private LocalDateTime completedAt;
}
