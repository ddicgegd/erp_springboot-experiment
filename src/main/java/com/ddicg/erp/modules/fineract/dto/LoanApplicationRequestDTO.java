package com.ddicg.erp.modules.fineract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplicationRequestDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Principal amount is required")
    @DecimalMin(value = "1.0", message = "Principal must be greater than 0")
    private BigDecimal principal;

    @NotNull(message = "Loan term frequency is required")
    @Min(value = 1, message = "Loan term frequency must be at least 1")
    private Integer loanTermFrequency;

    @NotNull(message = "Number of repayments is required")
    @Min(value = 1, message = "Number of repayments must be at least 1")
    private Integer numberOfRepayments;

    // Fineract default mappings
    private Integer loanTermFrequencyType = 2;
    private Integer repaymentEvery = 1;
    private Integer repaymentFrequencyType = 2;
    private BigDecimal interestRatePerPeriod = BigDecimal.ZERO;
    private Integer amortizationType = 1;
    private Integer interestType = 1;
    private Integer interestCalculationPeriodType = 1;
    private String transactionProcessingStrategyCode = "mifos-standard-strategy";
    private String loanType = "individual";
}
