package com.ddicg.erp.fineract.controller;

import com.ddicg.erp.fineract.service.FineractJournalService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
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

    @Data
    public static class AccountingRequest {
        @NotBlank(message = "Order ID is required")
        private String orderId;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        private BigDecimal amount;

        private String note;
    }
}
