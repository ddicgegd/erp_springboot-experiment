package com.ddicg.erp.service.dto.request;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConfirmOrderRequest {
    private String orderId;
    private String confirmationInfo;
    private LocalDateTime confirmedAt;
    private String confirmedBy;
}
