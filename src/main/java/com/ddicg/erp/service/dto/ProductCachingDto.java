package com.ddicg.erp.service.dto;

import lombok.*;

import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProductCachingDto {
    private String recommendationId;
    private String strategy;
    private long generatedAt;
    private List<ProductDto> items;
}
