package com.ddicg.erp.fineract.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FineractLoanAccountDTO {
    private Long id;
    private String accountNo;
    private String status;
    private Long clientId;
    private String clientName;
    private Long loanProductId;
    private String loanProductName;
    private BigDecimal principal;
    private BigDecimal totalRepayment;
    private BigDecimal totalOutstanding;
}
