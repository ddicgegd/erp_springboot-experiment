package com.ddicg.erp.fineract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanRepaymentRequestDTO {

    @NotNull(message = "Transaction amount is required")
    @DecimalMin(value = "1.0", message = "Transaction amount must be greater than 0")
    private BigDecimal transactionAmount;

    @NotBlank(message = "Note or receipt number is required for reconciliation")
    private String note;
}
