package com.ddicg.erp.modules.notification.kafka.producer;

import com.ddicg.erp.core.common.constants.KafkaTopics;
import com.ddicg.erp.modules.notification.dto.EmailDispatchPayload;
import com.ddicg.erp.modules.notification.dto.TemplateCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Producer gửi sự kiện thông báo email vào Kafka topic để xử lý bất đồng bộ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Đẩy payload gửi email bất kỳ vào Kafka queue.
     */
    public void dispatchEmail(EmailDispatchPayload payload) {
        if (payload == null) {
            log.warn("[NotificationProducer] Payload gửi email bị null, bỏ qua");
            return;
        }

        if (payload.getMessageId() == null || payload.getMessageId().isBlank()) {
            payload.setMessageId(UUID.randomUUID().toString());
        }

        if (payload.getRequestedAt() == null) {
            payload.setRequestedAt(LocalDateTime.now());
        }

        try {
            String messageJson = objectMapper.writeValueAsString(payload);
            String partitionKey = payload.getRecipient() != null ? payload.getRecipient() : payload.getMessageId();
            kafkaTemplate.send(KafkaTopics.NOTIFICATION_EMAIL_TOPIC, partitionKey, messageJson);
            log.info("[NotificationProducer] Đã phát Kafka event gửi mail messageId={} tới {}", payload.getMessageId(), payload.getRecipient());
        } catch (Exception e) {
            log.error("[NotificationProducer] Lỗi khi đẩy Kafka event gửi email cho {}: {}", payload.getRecipient(), e.getMessage(), e);
        }
    }

    /**
     * Tiện ích gửi email xác thực tài khoản.
     */
    public void sendVerificationEmail(String recipient, String username, String verificationUrl, String token) {
        String resolvedToken = token;
        if ((resolvedToken == null || resolvedToken.isBlank()) && verificationUrl != null && verificationUrl.contains("token=")) {
            resolvedToken = verificationUrl.substring(verificationUrl.indexOf("token=") + 6);
            if (resolvedToken.contains("&")) {
                resolvedToken = resolvedToken.substring(0, resolvedToken.indexOf("&"));
            }
        }

        Map<String, String> params = new HashMap<>();
        params.put("subject", TemplateCode.VERIFICATION_EMAIL.getDefaultSubject());
        params.put("username", username);
        params.put("email", recipient);
        params.put("recipient", recipient);
        params.put("verificationUrl", verificationUrl);
        params.put("token", resolvedToken);
        params.put("expiryMinutes", "5");

        EmailDispatchPayload payload = EmailDispatchPayload.builder()
                .messageId(UUID.randomUUID().toString())
                .recipient(recipient)
                .subject(TemplateCode.VERIFICATION_EMAIL.getDefaultSubject())
                .templateCode(TemplateCode.VERIFICATION_EMAIL.name())
                .params(params)
                .deduplicationKey("VERIFY:" + recipient + ":" + (resolvedToken != null ? resolvedToken : verificationUrl))
                .priority(1)
                .requestedAt(LocalDateTime.now())
                .build();

        dispatchEmail(payload);
    }

    /**
     * Tiện ích gửi email khôi phục tài khoản / đặt lại mật khẩu.
     */
    public void sendAccountRecoveryEmail(String recipient, String username, String resetUrl, String token) {
        String resolvedToken = token;
        if ((resolvedToken == null || resolvedToken.isBlank()) && resetUrl != null && resetUrl.contains("token=")) {
            resolvedToken = resetUrl.substring(resetUrl.indexOf("token=") + 6);
            if (resolvedToken.contains("&")) {
                resolvedToken = resolvedToken.substring(0, resolvedToken.indexOf("&"));
            }
        }

        Map<String, String> params = new HashMap<>();
        params.put("subject", TemplateCode.ACCOUNT_RECOVERY.getDefaultSubject());
        params.put("username", username);
        params.put("email", recipient);
        params.put("recipient", recipient);
        params.put("resetUrl", resetUrl);
        params.put("token", resolvedToken);
        params.put("expiryMinutes", "10");

        EmailDispatchPayload payload = EmailDispatchPayload.builder()
                .messageId(UUID.randomUUID().toString())
                .recipient(recipient)
                .subject(TemplateCode.ACCOUNT_RECOVERY.getDefaultSubject())
                .templateCode(TemplateCode.ACCOUNT_RECOVERY.name())
                .params(params)
                .deduplicationKey("RECOVERY:" + recipient + ":" + (resolvedToken != null ? resolvedToken : resetUrl))
                .priority(1)
                .requestedAt(LocalDateTime.now())
                .build();

        dispatchEmail(payload);
    }
}
