package com.ddicg.erp.modules.subscription.controller;

import com.ddicg.erp.modules.subscription.dto.request.GooglePubSubMessageDto;
import com.ddicg.erp.modules.subscription.service.GoogleRtdnHandlerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class GooglePlayWebhookController {

    private final GoogleRtdnHandlerService rtdnHandlerService;
    private final ObjectMapper objectMapper;

    @PostMapping("/google-play-rtdn")
    public ResponseEntity<Void> handleGooglePlayRtdn(@RequestBody GooglePubSubMessageDto payload) {
        log.info("Received Google Play Pub/Sub webhook");
        if (payload == null || payload.getMessage() == null || payload.getMessage().getData() == null) {
            log.warn("Empty or invalid Pub/Sub payload");
            return ResponseEntity.ok().build();
        }

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(payload.getMessage().getData());
            String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);
            log.info("Decoded RTDN JSON: {}", decodedJson);

            JsonNode rootNode = objectMapper.readTree(decodedJson);
            JsonNode subNotificationNode = rootNode.path("subscriptionNotification");
            if (!subNotificationNode.isMissingNode()) {
                String purchaseToken = subNotificationNode.path("purchaseToken").asText();
                int notificationType = subNotificationNode.path("notificationType").asInt();

                if (purchaseToken != null && !purchaseToken.isBlank()) {
                    rtdnHandlerService.processNotification(purchaseToken, notificationType, decodedJson);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Google RTDN message: {}", e.getMessage(), e);
        }

        // Return 200 OK so Pub/Sub does not constantly retry corrupt payload
        return ResponseEntity.ok().build();
    }
}
