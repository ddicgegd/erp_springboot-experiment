package com.ddicg.erp.modules.order.dto.response;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NextStatusSimulationResponse {
    OrderStatus predictedStatus;
    String predictedStatusTitle;
    String diagnosticPrediction;
    LocalDateTime estimatedAt;
    String formattedEstimatedTime;
    Double probabilityScore;
    String riskSeverity;
    String riskCategory;
    String riskWarningMessage;
    List<String> prerequisiteTasks;
    String alternativeNextStatus;
    Double alternativeRiskRate;
}
