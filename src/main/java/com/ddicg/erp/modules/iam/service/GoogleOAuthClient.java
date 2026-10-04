package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.modules.iam.dto.GoogleTokenResponse;
import com.ddicg.erp.modules.iam.dto.GoogleUserInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Slf4j
@Service
public class GoogleOAuthClient {

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GoogleOAuthClient(ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Value("${application.oauth2.google.client-id:default-client-id}")
    private String clientId;

    @Value("${application.oauth2.google.client-secret:default-client-secret}")
    private String clientSecret;

    @Value("${application.oauth2.google.redirect-uri:http://localhost:8080/api/auth/oauth2/callback/google}")
    private String redirectUri;

    public GoogleUserInfo exchangeCodeForUserInfo(String code) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", code);
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri", redirectUri);
        formData.add("grant_type", "authorization_code");

        GoogleTokenResponse tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (Exception e) {
            log.error("Failed to exchange code for Google token", e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (tokenResponse == null || tokenResponse.getIdToken() == null) {
            log.error("Invalid token response from Google");
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        GoogleUserInfo userInfo = parseIdToken(tokenResponse.getIdToken());

        if (Boolean.FALSE.equals(userInfo.getEmailVerified()) || userInfo.getEmailVerified() == null) {
            throw new BusinessException(ErrorCode.UNVERIFIED_GOOGLE_EMAIL);
        }

        return userInfo;
    }

    private GoogleUserInfo parseIdToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid ID token format");
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return objectMapper.readValue(payload, GoogleUserInfo.class);
        } catch (Exception e) {
            log.error("Failed to parse ID token", e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}