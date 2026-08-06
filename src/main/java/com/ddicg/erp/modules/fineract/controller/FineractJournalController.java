package com.ddicg.erp.modules.fineract.controller;

import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/erp/accounting")
@RequiredArgsConstructor
public class FineractJournalController {

    private final FineractJournalService journalService;

    @PostMapping("/sales")
    public ResponseEntity<JsonNode> recordSale(@Valid @RequestBody AccountingRequest payload) {
        return ResponseEntity.ok(journalService.recordSale(payload.getOrderId(), payload.getAmount(), payload.getNote()));
    }

    @PostMapping("/refunds")
    public ResponseEntity<JsonNode> recordRefund(@Valid @RequestBody AccountingRequest payload) {
        return ResponseEntity.ok(journalService.recordRefund(payload.getOrderId(), payload.getAmount(), payload.getNote()));
    }

    public static class AccountingRequest {
        @NotBlank(message = "Order ID is required")
        private String orderId;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        private BigDecimal amount;

        private String note;

        public AccountingRequest() {}

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}
