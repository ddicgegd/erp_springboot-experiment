package com.ddicg.erp.core.common.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {
    private String name;
    private Double discountPercent;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
