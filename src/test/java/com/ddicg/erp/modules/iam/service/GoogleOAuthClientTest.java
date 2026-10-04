package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.modules.iam.dto.GoogleTokenResponse;
import com.ddicg.erp.modules.iam.dto.GoogleUserInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(GoogleOAuthClient.class)
public class GoogleOAuthClientTest {

    @Autowired
    private GoogleOAuthClient googleOAuthClient;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        server.reset();
    }

    private String createMockIdToken(GoogleUserInfo userInfo) throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(userInfo));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString("mock-signature".getBytes());
        return header + "." + payload + "." + signature;
    }

    @Test
    public void exchangeCodeForUserInfo_Success() throws Exception {
        GoogleUserInfo mockUserInfo = new GoogleUserInfo();
        mockUserInfo.setSub("12345");
        mockUserInfo.setEmail("test@google.com");
        mockUserInfo.setEmailVerified(true);
        mockUserInfo.setName("Test User");
        
        GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
        tokenResponse.setAccessToken("access-token");
        tokenResponse.setIdToken(createMockIdToken(mockUserInfo));
        tokenResponse.setExpiresIn(3600L);
        
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess(objectMapper.writeValueAsString(tokenResponse), MediaType.APPLICATION_JSON));
                
        GoogleUserInfo result = googleOAuthClient.exchangeCodeForUserInfo("auth-code");
        
        assertNotNull(result);
        assertEquals("12345", result.getSub());
        assertEquals("test@google.com", result.getEmail());
        assertTrue(result.getEmailVerified());
        
        server.verify();
    }

    @Test
    public void exchangeCodeForUserInfo_UnverifiedEmail_ShouldThrowException() throws Exception {
        GoogleUserInfo mockUserInfo = new GoogleUserInfo();
        mockUserInfo.setSub("12345");
        mockUserInfo.setEmail("unverified@google.com");
        mockUserInfo.setEmailVerified(false);
        
        GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
        tokenResponse.setAccessToken("access-token");
        tokenResponse.setIdToken(createMockIdToken(mockUserInfo));
        
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(tokenResponse), MediaType.APPLICATION_JSON));
                
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            googleOAuthClient.exchangeCodeForUserInfo("auth-code");
        });
        
        assertEquals(ErrorCode.UNVERIFIED_GOOGLE_EMAIL, exception.getErrorCode());
        
        server.verify();
    }
}