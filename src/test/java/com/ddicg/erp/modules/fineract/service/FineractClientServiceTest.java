package com.ddicg.erp.modules.fineract.service;

import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.modules.fineract.config.FineractProperties;
import com.ddicg.erp.modules.fineract.dto.FineractClientCreateRequestDTO;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FineractClientServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.ddicg.erp.core.security.SecurityUtil securityUtil;

    @Spy
    private FineractProperties fineractProperties = new FineractProperties();

    @InjectMocks
    private FineractClientService clientService;

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

    @BeforeEach
    void setUp() {
        fineractProperties.setOfficeId(1L);
        fineractProperties.setLegalFormId(1L);
        fineractProperties.setDateFormat("dd MMMM yyyy");
        fineractProperties.setLocale("en");
    }

    @Test
    @DisplayName("User already has fineractClientId: Return immediately, zero external calls")
    void testGetOrCreateFineractClient_AlreadySynced() {
        User user = new User();
        user.setId(10L);
        user.setFineractClientId("999");

        String clientId = clientService.getOrCreateFineractClient(user);

        assertThat(clientId).isEqualTo("999");
        verifyNoInteractions(restClient);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("User has empty or null full name: Throw USER_PROFILE_INCOMPLETE, zero fake client created")
    void testGetOrCreateFineractClient_NullFullName_ThrowsError() {
        User user = new User();
        user.setId(10L);
        user.setFullName(null);

        assertThatThrownBy(() -> clientService.getOrCreateFineractClient(user))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_PROFILE_INCOMPLETE);

        verifyNoInteractions(restClient);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Idempotency: Client already exists on Fineract by externalId -> Attach and save without creating new")
    void testGetOrCreateFineractClient_AlreadyExistsOnFineract() {
        User user = new User();
        user.setId(10L);
        user.setFullName("Ngô Ngọc Định");

        // Mock GET /clients?externalId=10
        ObjectNode mockResponse = mapper.createObjectNode();
        ArrayNode pageItems = mockResponse.putArray("pageItems");
        ObjectNode item = pageItems.addObject();
        item.put("id", "888");

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/clients?externalId={externalId}"), eq("10"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(mockResponse);

        String result = clientService.getOrCreateFineractClient(user);

        assertThat(result).isEqualTo("888");
        assertThat(user.getFineractClientId()).isEqualTo("888");
        verify(userRepository).save(user);
        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("New user with Vietnamese name: Parse correctly and create Fineract client with rich identity")
    void testGetOrCreateFineractClient_CreatesNewClientSuccessfully() {
        User user = new User();
        user.setId(5L);
        user.setFullName("Ngô Ngọc Định");
        user.setEmail("admin@example.com");
        user.setPhoneNumber("0971791373");

        // Mock GET /clients?externalId=5 -> empty list (not found)
        ObjectNode emptyResponse = mapper.createObjectNode();
        emptyResponse.putArray("pageItems");

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/clients?externalId={externalId}"), eq("5"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(emptyResponse);

        // Mock POST /clients -> return created clientId
        ObjectNode createResponse = mapper.createObjectNode();
        createResponse.put("clientId", "101");

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/clients"))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(FineractClientCreateRequestDTO.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(createResponse);

        String result = clientService.getOrCreateFineractClient(user);

        assertThat(result).isEqualTo("101");
        assertThat(user.getFineractClientId()).isEqualTo("101");
        verify(userRepository).save(user);

        // Verify request payload fields
        ArgumentCaptor<FineractClientCreateRequestDTO> captor = ArgumentCaptor.forClass(FineractClientCreateRequestDTO.class);
        verify(requestBodySpec).body(captor.capture());
        FineractClientCreateRequestDTO captured = captor.getValue();

        assertThat(captured.getFirstname()).isEqualTo("Định");
        assertThat(captured.getLastname()).isEqualTo("Ngô Ngọc");
        assertThat(captured.getExternalId()).isEqualTo("5");
        assertThat(captured.getEmailAddress()).isEqualTo("admin@example.com");
        assertThat(captured.getMobileNo()).isEqualTo("0971791373");
        assertThat(captured.getActive()).isTrue();
    }

    @Test
    @DisplayName("GET clients for Admin: Queries all /clients")
    void testGetClients_Admin_ReturnsAll() {
        when(securityUtil.isStaffOrAdmin()).thenReturn(true);

        ObjectNode allClients = mapper.createObjectNode();
        allClients.put("totalFilteredRecords", 10);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/clients"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(allClients);

        JsonNode result = clientService.getClients();

        assertThat(result.path("totalFilteredRecords").asInt()).isEqualTo(10);
    }

    @Test
    @DisplayName("GET clients for regular User: Queries /clients?externalId={userId}")
    void testGetClients_RegularUser_ReturnsSelf() {
        when(securityUtil.isStaffOrAdmin()).thenReturn(false);
        when(securityUtil.getCurrentUserId()).thenReturn("42");

        ObjectNode userClient = mapper.createObjectNode();
        userClient.put("totalFilteredRecords", 1);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/clients?externalId={externalId}"), eq("42"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(userClient);

        JsonNode result = clientService.getClients();

        assertThat(result.path("totalFilteredRecords").asInt()).isEqualTo(1);
    }
}
