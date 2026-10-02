package com.ddicg.erp.modules.subscription.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.subscription.dto.request.GooglePubSubMessageDto;
import com.ddicg.erp.modules.subscription.dto.request.GooglePurchaseVerifyRequest;
import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.service.GooglePlayVerificationService;
import com.ddicg.erp.modules.subscription.service.GoogleRtdnHandlerService;
import com.ddicg.erp.modules.subscription.service.SubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private GooglePlayVerificationService verificationService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private GoogleRtdnHandlerService rtdnHandlerService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SubscriptionController subscriptionController;

    private GooglePlayWebhookController webhookController;

    @BeforeEach
    void setUp() {
        webhookController = new GooglePlayWebhookController(rtdnHandlerService, objectMapper);
    }

    @Test
    @DisplayName("SubscriptionController - verifyGooglePurchase API success")
    void testVerifyGooglePurchase() {
        GooglePurchaseVerifyRequest req = GooglePurchaseVerifyRequest.builder()
                .purchaseToken("token_123")
                .planCode("GOLD_3M")
                .build();

        UserSubscriptionResponse mockRes = UserSubscriptionResponse.builder()
                .id(1L)
                .userId(1L)
                .planCode("GOLD_3M")
                .subscriptionState(SubscriptionState.TRIALING)
                .isTrial(true)
                .startTime(LocalDateTime.now())
                .expiryTime(LocalDateTime.now().plusDays(30))
                .build();

        when(verificationService.verifyAndActivate(eq(1L), any(GooglePurchaseVerifyRequest.class)))
                .thenReturn(mockRes);

        Response<UserSubscriptionResponse> apiRes = subscriptionController.verifyGooglePurchase(1L, req);

        assertNotNull(apiRes);
        assertEquals(mockRes, apiRes.getData());
        assertNotNull(apiRes.getStatus());
        assertEquals("Kích hoạt gói Google Play thành công", apiRes.getStatus().getMessage());
    }

    @Test
    @DisplayName("SubscriptionController - getMySubscription returns user active subscription")
    void testGetMySubscription() {
        UserSubscriptionResponse mockRes = UserSubscriptionResponse.builder()
                .id(1L)
                .userId(1L)
                .subscriptionState(SubscriptionState.ACTIVE)
                .build();

        when(subscriptionService.getLatestSubscriptionForUser(1L)).thenReturn(Optional.of(mockRes));

        Response<UserSubscriptionResponse> apiRes = subscriptionController.getMySubscription(1L);

        assertNotNull(apiRes);
        assertEquals(mockRes, apiRes.getData());
    }

    @Test
    @DisplayName("GooglePlayWebhookController - Decodes PubSub base64 payload and calls RTDN service")
    void testWebhookDecodeAndDispatch() {
        String jsonPayload = "{\"version\":\"1.0\",\"subscriptionNotification\":{\"version\":\"1.0\",\"notificationType\":2,\"purchaseToken\":\"sub_token_abc\",\"subscriptionId\":\"sub_gold\"}}";
        String base64Data = Base64.getEncoder().encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));

        GooglePubSubMessageDto dto = GooglePubSubMessageDto.builder()
                .message(GooglePubSubMessageDto.PubSubMessage.builder()
                        .data(base64Data)
                        .messageId("msg_123")
                        .build())
                .subscription("projects/erp/subscriptions/rtdn-sub")
                .build();

        ResponseEntity<Void> response = webhookController.handleGooglePlayRtdn(dto);

        assertEquals(200, response.getStatusCode().value());
        verify(rtdnHandlerService).processNotification(eq("sub_token_abc"), eq(2), eq(jsonPayload));
    }
}
