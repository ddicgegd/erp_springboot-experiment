package com.ddicg.erp.modules.fineract.service;

import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.fineract.config.FineractProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class FineractLoanService {

    private final RestClient fineractRestClient;
    private final FineractClientService clientService;
    private final SecurityUtil securityUtil;
    private final FineractProperties fineractProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String resolveCurrentClientId() {
        if (clientService != null) {
            try {
                return clientService.getOrCreateCurrentClient();
            } catch (Exception e) {
                log.debug("Không thể auto-sync client từ clientService: {}", e.getMessage());
            }
        }
        if (securityUtil != null) {
            return securityUtil.getCurrentFineractClientId();
        }
        return null;
    }

    public JsonNode getLoans() {
        if (securityUtil != null && securityUtil.isStaffOrAdmin()) {
            return fineractRestClient.get()
                    .uri("/loans")
                    .retrieve()
                    .body(JsonNode.class);
        }
        String clientId = resolveCurrentClientId();
        if (clientId == null || clientId.isBlank()) {
            ObjectNode empty = objectMapper.createObjectNode();
            empty.put("totalFilteredRecords", 0);
            empty.putArray("pageItems");
            return empty;
        }
        return fineractRestClient.get()
                .uri("/loans?clientId={clientId}", clientId)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getLoansForUser(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            ObjectNode empty = objectMapper.createObjectNode();
            empty.put("totalFilteredRecords", 0);
            empty.putArray("pageItems");
            return empty;
        }
        return fineractRestClient.get()
                .uri("/loans?clientId={clientId}", clientId)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getLoanDetails(Long loanId) {
        return fineractRestClient.get()
                .uri("/loans/{loanId}?associations=all", loanId)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getLoanTemplate(Long productId) {
        String clientId = resolveCurrentClientId();
        if (clientId != null && !clientId.isBlank()) {
            return fineractRestClient.get()
                    .uri("/loans/template?templateType=individual&productId={productId}&clientId={clientId}", productId, clientId)
                    .retrieve()
                    .body(JsonNode.class);
        }
        return fineractRestClient.get()
                .uri("/loans/template?templateType=individual&productId={productId}", productId)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getLoanTemplate(Long productId, Long clientId) {
        if (clientId != null) {
            return fineractRestClient.get()
                    .uri("/loans/template?templateType=individual&productId={productId}&clientId={clientId}", productId, clientId)
                    .retrieve()
                    .body(JsonNode.class);
        }
        return getLoanTemplate(productId);
    }

    public JsonNode applyLoan(JsonNode payload) {
        JsonNode enrichedPayload = payload;
        if (payload instanceof ObjectNode objNode) {
            // Auto-inject clientId nếu chưa có
            if (!objNode.has("clientId")) {
                String clientId = resolveCurrentClientId();
                if (clientId != null && !clientId.isBlank()) {
                    objNode.put("clientId", Long.parseLong(clientId));
                }
            }
            // Auto-inject locale và dateFormat — bắt buộc khi có loanTermFrequency / principal
            if (!objNode.has("locale")) {
                String locale = fineractProperties.getLocale();
                objNode.put("locale", locale != null ? locale : "en");
            }
            if (!objNode.has("dateFormat")) {
                String dateFormat = fineractProperties.getDateFormat();
                objNode.put("dateFormat", dateFormat != null ? dateFormat : "dd MMMM yyyy");
            }
            enrichedPayload = objNode;
        }
        return fineractRestClient.post()
                .uri("/loans")
                .body(enrichedPayload)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode executeCommand(Long loanId, String command, JsonNode payload) {
        return fineractRestClient.post()
                .uri("/loans/{loanId}?command={command}", loanId, command)
                .body(payload != null ? payload : objectMapper.createObjectNode())
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode repayLoan(Long loanId, JsonNode payload) {
        return fineractRestClient.post()
                .uri("/loans/{loanId}/transactions?command=repayment", loanId)
                .body(payload != null ? payload : objectMapper.createObjectNode())
                .retrieve()
                .body(JsonNode.class);
    }
}
