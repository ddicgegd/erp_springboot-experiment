package com.ddicg.erp.modules.fineract.dto;

import java.math.BigDecimal;

public class JournalEntryLineDTO {
    private Long glAccountId;
    private BigDecimal amount;

    public JournalEntryLineDTO() {}
    public JournalEntryLineDTO(Long glAccountId, BigDecimal amount) {
        this.glAccountId = glAccountId;
        this.amount = amount;
    }

    public Long getGlAccountId() { return glAccountId; }
    public void setGlAccountId(Long glAccountId) { this.glAccountId = glAccountId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
