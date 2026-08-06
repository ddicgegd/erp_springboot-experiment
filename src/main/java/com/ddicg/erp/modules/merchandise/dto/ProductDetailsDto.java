package com.ddicg.erp.modules.merchandise.dto;

import jakarta.persistence.Column;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProductDetailsDto {
    @Column(name = "sku")
    String sku;

    int quantity;
}
