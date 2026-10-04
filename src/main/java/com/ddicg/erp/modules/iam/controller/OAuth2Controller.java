package com.ddicg.erp.modules.iam.controller;

import com.ddicg.erp.modules.iam.service.OAuth2StateStore;
import com.ddicg.erp.modules.iam.dto.GoogleUserInfo;
import com.ddicg.erp.modules.iam.dto.request.OAuth2TokenRequest;
import com.ddicg.erp.modules.iam.dto.response.OAuth2TokenResponse;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.service.AuthTicketStore;
import com.ddicg.erp.modules.iam.service.GoogleOAuthClient;
import com.ddicg.erp.modules.iam.service.iUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;

@RestController
@RequestMapping("/api/auth/oauth2")
@RequiredArgsConstructor
public class OAuth2Controller {

    private final OAuth2StateStore oAuth2StateStore;
    private final GoogleOAuthClient googleOAuthClient;
    private final iUser userService;
    private final AuthTicketStore authTicketStore;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${application.oauth2.google.client-id:default-client-id}")
    private String clientId;

    @Value("${application.oauth2.google.redirect-uri:http://localhost:8080/api/auth/oauth2/callback/google}")
    private String redirectUri;

    @Value("${application.oauth2.frontend-callback-url:https://erp.ddicg.com/auth/callback}")
    private String frontendCallbackUrl;

    @GetMapping("/authorize/google")
    public ResponseEntity<Void> authorizeGoogle() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        oAuth2StateStore.saveState(state);

        ResponseCookie cookie = ResponseCookie.from("oauth_state", state)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/auth/oauth2")
                .maxAge(600)
                .build();

        String googleAuthUrl = UriComponentsBuilder.fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("access_type", "offline")
                .build().encode().toUriString();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .location(URI.create(googleAuthUrl))
                .build();
    }

    @GetMapping("/callback/google")
    public ResponseEntity<Void> callbackGoogle(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String queryState,
            @CookieValue(name = "oauth_state", required = false) String cookieState,
            HttpServletResponse response) {

        ResponseCookie deleteCookie = ResponseCookie.from("oauth_state", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/auth/oauth2")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());

        oAuth2StateStore.validateAndConsume(cookieState, queryState);

        GoogleUserInfo userInfo = googleOAuthClient.exchangeCodeForUserInfo(code);
        User user = userService.processOAuth2User(userInfo);
        String ticket = authTicketStore.createTicket(user.getId());

        String redirectUrl = UriComponentsBuilder.fromHttpUrl(frontendCallbackUrl)
                .queryParam("ticket", ticket)
                .build().toUriString();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .location(URI.create(redirectUrl))
                .build();
    }

    @PostMapping("/token")
    public ResponseEntity<OAuth2TokenResponse> issueTokens(@RequestBody OAuth2TokenRequest request) {
        OAuth2TokenResponse tokenResponse = userService.issueTokensFromTicket(request.getTicket());
        return ResponseEntity.ok(tokenResponse);
    }
}