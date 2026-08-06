package com.ddicg.erp.modules.fineract.service;

import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.fineract.config.FineractProperties;
import com.ddicg.erp.modules.fineract.dto.LoanApplicationRequestDTO;
import com.ddicg.erp.modules.fineract.dto.LoanRepaymentRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class FineractLoanService {

    private final RestClient fineractRestClient;
    private final FineractClientService clientService;
    private final FineractProperties fineractProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode getLoans() {
        return fineractRestClient.get()
                .uri("/loans")
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getLoansForUser(User user) {
        String clientId = clientService.getOrCreateFineractClient(user);
        return fineractRestClient.get()
                .uri("/loans?clientId={clientId}", clientId)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode applyLoanForUser(User user, LoanApplicationRequestDTO requestDTO) {
        String clientId = clientService.getOrCreateFineractClient(user);
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern(fineractProperties.getDateFormat()));

        ObjectNode payload = objectMapper.valueToTree(requestDTO);
        payload.put("clientId", Long.parseLong(clientId));
        payload.put("expectedDisbursementDate", currentDate);
        payload.put("submittedOnDate", currentDate);
        payload.put("dateFormat", fineractProperties.getDateFormat());
        payload.put("locale", fineractProperties.getLocale());

        return fineractRestClient.post()
                .uri("/loans")
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode repayLoanForUser(User user, Long loanId, LoanRepaymentRequestDTO requestDTO) {
        // Enforce lazy sync registration checks
        clientService.getOrCreateFineractClient(user);
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern(fineractProperties.getDateFormat()));

        ObjectNode payload = objectMapper.valueToTree(requestDTO);
        payload.put("transactionDate", currentDate);
        payload.put("dateFormat", fineractProperties.getDateFormat());
        payload.put("locale", fineractProperties.getLocale());

        return fineractRestClient.post()
                .uri("/loans/{loanId}/transactions?command=repayment", loanId)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
    }
}
