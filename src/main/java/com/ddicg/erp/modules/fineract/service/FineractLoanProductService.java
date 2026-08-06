package com.ddicg.erp.modules.fineract.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class FineractLoanProductService {

    private final RestClient fineractRestClient;

    public JsonNode getLoanProducts() {
        return fineractRestClient.get()
                .uri("/loanproducts")
                .retrieve()
                .body(JsonNode.class);
    }
}
