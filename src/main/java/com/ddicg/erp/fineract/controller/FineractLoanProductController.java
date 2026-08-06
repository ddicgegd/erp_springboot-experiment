package com.ddicg.erp.fineract.controller;

import com.ddicg.erp.fineract.service.FineractLoanProductService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/erp/loan-products")
@RequiredArgsConstructor
public class FineractLoanProductController {

    private final FineractLoanProductService loanProductService;

    @GetMapping
    public ResponseEntity<JsonNode> getLoanProducts() {
        return ResponseEntity.ok(loanProductService.getLoanProducts());
    }
}
