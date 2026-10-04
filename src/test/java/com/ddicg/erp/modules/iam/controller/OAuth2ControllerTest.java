package com.ddicg.erp.modules.iam.controller;

import com.ddicg.erp.core.config.SecurityConfiguration;
import com.ddicg.erp.core.security.JwtAuthenticationFilter;
import com.ddicg.erp.modules.iam.service.OAuth2StateStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.ddicg.erp.modules.iam.dto.GoogleUserInfo;
import com.ddicg.erp.modules.iam.dto.request.OAuth2TokenRequest;
import com.ddicg.erp.modules.iam.dto.response.OAuth2TokenResponse;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.service.AuthTicketStore;
import com.ddicg.erp.modules.iam.service.GoogleOAuthClient;
import com.ddicg.erp.modules.iam.service.iUser;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OAuth2Controller.class)
@AutoConfigureMockMvc(addFilters = false)
public class OAuth2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OAuth2StateStore oAuth2StateStore;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.ddicg.erp.core.security.SecurityUtil securityUtil;

    @MockBean
    private GoogleOAuthClient googleOAuthClient;

    @MockBean
    private iUser userService;

    @MockBean
    private AuthTicketStore authTicketStore;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void authorizeGoogle_ShouldRedirectWithCookie() throws Exception {
        doNothing().when(oAuth2StateStore).saveState(anyString());

        mockMvc.perform(get("/api/auth/oauth2/authorize/google"))
                .andExpect(status().isFound())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("https://accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("response_type=code")))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("scope=openid%20email%20profile")))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("access_type=offline")))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("state=")))
                .andExpect(cookie().exists("oauth_state"))
                .andExpect(cookie().httpOnly("oauth_state", true))
                .andExpect(cookie().secure("oauth_state", true))
                .andExpect(cookie().path("oauth_state", "/api/auth/oauth2"))
                .andExpect(cookie().maxAge("oauth_state", 600));

        verify(oAuth2StateStore).saveState(anyString());
    }

    @Test
    public void callbackGoogle_MissingCookie_ShouldReturn403() throws Exception {
        org.mockito.Mockito.when(oAuth2StateStore.validateAndConsume(null, "some-state"))
                .thenThrow(new com.ddicg.erp.core.exception.BusinessException(com.ddicg.erp.core.exception.ErrorCode.INVALID_OAUTH_STATE_MISMATCH));

        mockMvc.perform(get("/api/auth/oauth2/callback/google")
                        .param("code", "some-code")
                        .param("state", "some-state"))
                .andExpect(status().isForbidden())
                .andExpect(cookie().exists("oauth_state"))
                .andExpect(cookie().maxAge("oauth_state", 0));
    }

    @Test
    public void callbackGoogle_StateMismatch_ShouldReturn403() throws Exception {
        org.mockito.Mockito.when(oAuth2StateStore.validateAndConsume("cookie-state", "query-state"))
                .thenThrow(new com.ddicg.erp.core.exception.BusinessException(com.ddicg.erp.core.exception.ErrorCode.INVALID_OAUTH_STATE_MISMATCH));

        mockMvc.perform(get("/api/auth/oauth2/callback/google")
                        .param("code", "some-code")
                        .param("state", "query-state")
                        .cookie(new jakarta.servlet.http.Cookie("oauth_state", "cookie-state")))
                .andExpect(status().isForbidden())
                .andExpect(cookie().exists("oauth_state"))
                .andExpect(cookie().maxAge("oauth_state", 0));
    }

    @Test
    public void callbackGoogle_ReplayedState_ShouldReturn403() throws Exception {
        org.mockito.Mockito.when(oAuth2StateStore.validateAndConsume("same-state", "same-state"))
                .thenThrow(new com.ddicg.erp.core.exception.BusinessException(com.ddicg.erp.core.exception.ErrorCode.EXPIRED_OR_REPLAYED_STATE));

        mockMvc.perform(get("/api/auth/oauth2/callback/google")
                        .param("code", "some-code")
                        .param("state", "same-state")
                        .cookie(new jakarta.servlet.http.Cookie("oauth_state", "same-state")))
                .andExpect(status().isForbidden())
                .andExpect(cookie().exists("oauth_state"))
                .andExpect(cookie().maxAge("oauth_state", 0));
    }


    @Test
    public void callbackGoogle_ValidState_ShouldRedirectToFrontend() throws Exception {
        org.mockito.Mockito.when(oAuth2StateStore.validateAndConsume("valid-state", "valid-state")).thenReturn(true);

        GoogleUserInfo userInfo = new GoogleUserInfo();
        org.mockito.Mockito.when(googleOAuthClient.exchangeCodeForUserInfo("some-code")).thenReturn(userInfo);

        User user = new User();
        user.setId(1L);
        org.mockito.Mockito.when(userService.processOAuth2User(userInfo)).thenReturn(user);

        org.mockito.Mockito.when(authTicketStore.createTicket(1L)).thenReturn("test-ticket");

        mockMvc.perform(get("/api/auth/oauth2/callback/google")
                        .param("code", "some-code")
                        .param("state", "valid-state")
                        .cookie(new jakarta.servlet.http.Cookie("oauth_state", "valid-state")))
                .andExpect(status().isFound())
                .andExpect(cookie().exists("oauth_state"))
                .andExpect(cookie().maxAge("oauth_state", 0))
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("ticket=test-ticket")))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("access_token"))));
    }

    @Test
    public void issueTokens_Success() throws Exception {
        OAuth2TokenRequest request = new OAuth2TokenRequest();
        request.setTicket("valid-ticket");

        OAuth2TokenResponse response = OAuth2TokenResponse.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .build();

        org.mockito.Mockito.when(userService.issueTokensFromTicket("valid-ticket")).thenReturn(response);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access"))
                .andExpect(jsonPath("$.refresh_token").value("refresh"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600));
    }

    @Test
    public void issueTokens_InvalidTicket_ShouldReturn401() throws Exception {
        OAuth2TokenRequest request = new OAuth2TokenRequest();
        request.setTicket("invalid-ticket");

        org.mockito.Mockito.when(userService.issueTokensFromTicket("invalid-ticket"))
                .thenThrow(new com.ddicg.erp.core.exception.BusinessException(com.ddicg.erp.core.exception.ErrorCode.INVALID_OR_EXPIRED_TICKET));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}