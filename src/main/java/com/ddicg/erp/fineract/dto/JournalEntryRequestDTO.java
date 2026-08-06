package com.ddicg.erp.fineract.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryRequestDTO {

    @NotNull(message = "Office ID is required")
    private Long officeId;

    @NotBlank(message = "Transaction date is required")
    private String transactionDate;

    @NotBlank(message = "Currency code is required")
    private String currencyCode;

    @NotBlank(message = "Reference number is required")
    private String referenceNumber; // Order ID or Refund ID

    private String comments;

    @NotEmpty(message = "At least one credit entry is required")
    @Valid
    private List<JournalEntryLineDTO> credits;

    @NotEmpty(message = "At least one debit entry is required")
    @Valid
    private List<JournalEntryLineDTO> debits;

    private String dateFormat = "dd MMMM yyyy";
    private String locale = "en";
}
