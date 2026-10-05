package com.ddicg.erp.modules.order.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeliveryStepResponse {
    String stepKey;
    String title;
    String diagnosticDescription;
    LocalDateTime timestamp;
    String formattedTime;
    boolean completed;
    boolean active;
    boolean simulated;
    Double confidenceRate;
    String riskLevel;
    String riskReason;
    Integer estimatedDeviationMinutes;

    public boolean isFundingOrHandover() {
        return "Bàn giao đơn vị vận chuyển".equals(title) || simulated;
    }
}
