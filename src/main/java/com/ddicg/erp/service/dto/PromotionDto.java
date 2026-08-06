package com.ddicg.erp.service.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PromotionDto {
    private String name;
    private Double discountPercent;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
