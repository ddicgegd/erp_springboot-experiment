package com.ddicg.erp.modules.fineract.service;

import com.ddicg.erp.core.security.SecurityUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FineractLoanServiceTest {

    @Mock
    private RestClient fineractRestClient;

    @Mock
    private FineractClientService clientService;

    @Mock
    private SecurityUtil securityUtil;

    @InjectMocks
    private FineractLoanService loanService;

    private ObjectMapper mapper = new ObjectMapper();

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Test
    @DisplayName("GET loans for user when clientId is null: Pure read, returns empty array, ZERO external calls")
    void testGetLoansForUser_NullClientId_ReturnsEmpty() {
        JsonNode result = loanService.getLoansForUser(null);

        assertThat(result).isNotNull();
        assertThat(result.path("totalFilteredRecords").asInt()).isEqualTo(0);
        assertThat(result.path("pageItems").isArray()).isTrue();
        assertThat(result.path("pageItems").size()).isEqualTo(0);

        verifyNoInteractions(fineractRestClient);
    }

    @Test
    @DisplayName("GET loans for user when clientId is provided: Queries Core Banking")
    void testGetLoansForUser_WithClientId_QueriesLoans() {
        ObjectNode mockLoans = mapper.createObjectNode();
        mockLoans.put("totalFilteredRecords", 2);

        when(fineractRestClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/loans?clientId={clientId}"), eq("555"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(mockLoans);

        JsonNode result = loanService.getLoansForUser("555");

        assertThat(result.path("totalFilteredRecords").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Get loan details: Forwards to /loans/{loanId}?associations=all")
    void testGetLoanDetails() {
        ObjectNode loanDetails = mapper.createObjectNode().put("id", 10L);

        when(fineractRestClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/loans/{loanId}?associations=all"), eq(10L))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(loanDetails);

        JsonNode result = loanService.getLoanDetails(10L);

        assertThat(result.path("id").asLong()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Apply loan: Forwards payload directly to /loans")
    void testApplyLoan() {
        ObjectNode payload = mapper.createObjectNode().put("principal", 10000000);
        ObjectNode createdResponse = mapper.createObjectNode().put("loanId", 100L);

        when(fineractRestClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/loans"))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(payload)).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(createdResponse);

        JsonNode result = loanService.applyLoan(payload);

        assertThat(result.path("loanId").asLong()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Execute command: Forwards to /loans/{loanId}?command={command}")
    void testExecuteCommand() {
        ObjectNode payload = mapper.createObjectNode().put("note", "Approved");
        ObjectNode responseNode = mapper.createObjectNode().put("loanId", 10L);

        when(fineractRestClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/loans/{loanId}?command={command}"), eq(10L), eq("approve"))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(payload)).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(responseNode);

        JsonNode result = loanService.executeCommand(10L, "approve", payload);

        assertThat(result.path("loanId").asLong()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Repay loan: Forwards to /loans/{loanId}/transactions?command=repayment")
    void testRepayLoan() {
        ObjectNode payload = mapper.createObjectNode().put("transactionAmount", 500000);
        ObjectNode responseNode = mapper.createObjectNode().put("resourceId", 1L);

        when(fineractRestClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/loans/{loanId}/transactions?command=repayment"), eq(10L))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(payload)).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(responseNode);

        JsonNode result = loanService.repayLoan(10L, payload);

        assertThat(result.path("resourceId").asLong()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET loans for Admin: Queries all /loans")
    void testGetLoans_Admin_QueriesAllLoans() {
        when(securityUtil.isStaffOrAdmin()).thenReturn(true);

        ObjectNode allLoans = mapper.createObjectNode();
        allLoans.put("totalFilteredRecords", 5);

        when(fineractRestClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/loans"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(allLoans);

        JsonNode result = loanService.getLoans();

        assertThat(result.path("totalFilteredRecords").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("GET loans for regular User: Resolves clientId and queries /loans?clientId={id}")
    void testGetLoans_RegularUser_ResolvesClientId() {
        when(securityUtil.isStaffOrAdmin()).thenReturn(false);
        when(clientService.getOrCreateCurrentClient()).thenReturn("777");

        ObjectNode userLoans = mapper.createObjectNode();
        userLoans.put("totalFilteredRecords", 1);

        when(fineractRestClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/loans?clientId={clientId}"), eq("777"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(userLoans);

        JsonNode result = loanService.getLoans();

        assertThat(result.path("totalFilteredRecords").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("GET loan template with productId only: Injects resolved clientId")
    void testGetLoanTemplate_AutoInjectsClientId() {
        when(clientService.getOrCreateCurrentClient()).thenReturn("777");

        ObjectNode mockTemplate = mapper.createObjectNode().put("clientId", 777L);

        when(fineractRestClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/loans/template?templateType=individual&productId={productId}&clientId={clientId}"), eq(1L), eq("777"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(mockTemplate);

        JsonNode result = loanService.getLoanTemplate(1L);

        assertThat(result.path("clientId").asLong()).isEqualTo(777L);
    }
}
