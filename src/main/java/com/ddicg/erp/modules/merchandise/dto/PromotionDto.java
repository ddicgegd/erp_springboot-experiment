package com.ddicg.erp.modules.merchandise.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PromotionDto {
    private String name;
    private Double discountPercent;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
