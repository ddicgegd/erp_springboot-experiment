package com.ddicg.erp.modules.fineract.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FineractLoanProductDTO {
    private Long id;
    private String name;
    private String shortName;
    private String description;
    private String currencyCode;
    private BigDecimal minPrincipal;
    private BigDecimal maxPrincipal;
    private String status;
}
