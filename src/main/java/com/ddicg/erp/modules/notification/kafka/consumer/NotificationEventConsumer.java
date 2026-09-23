package com.ddicg.erp.modules.notification.kafka.consumer;

import com.ddicg.erp.core.common.constants.KafkaTopics;
import com.ddicg.erp.modules.notification.config.NotificationEmailConfig;
import com.ddicg.erp.modules.notification.dto.EmailDeliveryResult;
import com.ddicg.erp.modules.notification.dto.EmailDispatchPayload;
import com.ddicg.erp.modules.notification.service.EmailProtectionService;
import com.ddicg.erp.modules.notification.service.EmailSenderService;
import com.ddicg.erp.modules.notification.service.EmailTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Consumer lắng nghe sự kiện từ notification-email-topic và điều phối quy trình gửi email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final EmailProtectionService emailProtectionService;
    private final EmailTemplateService emailTemplateService;
    private final EmailSenderService emailSenderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.NOTIFICATION_EMAIL_TOPIC,
            groupId = "notification-email-group",
            containerFactory = "kafkaListenerContainerFactory",
            properties = {"auto.offset.reset=earliest", "enable.auto.commit=false"}
    )
    public void consume(Object msg,
                        @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        try {
            if (msg instanceof ConsumerRecord<?, ?> record) {
                if (key == null && record.key() != null) {
                    key = String.valueOf(record.key());
                }
                msg = record.value();
            }

            EmailDispatchPayload payload = parsePayload(msg);
            if (payload == null || payload.getRecipient() == null || payload.getRecipient().isBlank()) {
                log.warn("[NotificationConsumer] Bỏ qua message không hợp lệ hoặc thiếu recipient: {}", msg);
                return;
            }

            processEmailDispatch(payload);
        } catch (Exception e) {
            log.error("[NotificationConsumer] Lỗi khi xử lý Kafka event gửi email: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi gửi email Kafka", e);
        }
    }

    /**
     * Pipeline xử lý gửi email đồng bộ/bất đồng bộ sau khi nhận từ queue.
     */
    @Async(NotificationEmailConfig.NOTIFICATION_TASK_EXECUTOR)
    public EmailDeliveryResult processEmailDispatch(EmailDispatchPayload payload) {
        String recipient = payload.getRecipient().trim();
        String messageId = payload.getMessageId();
        String dedupKey = payload.getDeduplicationKey();

        // 1. Kiểm tra Deduplication trên Redis
        if (dedupKey != null && !dedupKey.isBlank()) {
            boolean acquired = emailProtectionService.acquireDeduplicationLock(dedupKey, 10);
            if (!acquired) {
                return EmailDeliveryResult.duplicate(messageId, recipient, dedupKey);
            }
        }

        // 2. Kiểm tra Rate Limiting trên Redis (tối đa 5 mail / phút / người nhận)
        boolean allowed = emailProtectionService.allowDeliveryRate(recipient, 5);
        if (!allowed) {
            return EmailDeliveryResult.rateLimited(messageId, recipient);
        }

        // 3. Render HTML template
        try {
            String htmlContent = emailTemplateService.renderHtml(payload.getTemplateCode(), payload.getParams());
            String subject = (payload.getSubject() != null && !payload.getSubject().isBlank())
                    ? payload.getSubject()
                    : emailTemplateService.resolveDefaultSubject(payload.getTemplateCode());

            // 4. Gửi email qua SMTP
            emailSenderService.sendHtmlEmail(recipient, subject, htmlContent);
            return EmailDeliveryResult.success(messageId, recipient);
        } catch (Exception e) {
            log.error("[NotificationConsumer] Gửi email thất bại cho messageId={} recipient={}: {}", messageId, recipient, e.getMessage(), e);
            return EmailDeliveryResult.failed(messageId, recipient, e.getMessage());
        }
    }

    private EmailDispatchPayload parsePayload(Object msg) throws Exception {
        while (msg instanceof ConsumerRecord<?, ?> record) {
            msg = record.value();
        }
        if (msg == null) {
            return null;
        }
        if (msg instanceof EmailDispatchPayload payload) {
            return payload;
        } else if (msg instanceof String str) {
            if (str.isBlank()) {
                return null;
            }
            return objectMapper.readValue(str, EmailDispatchPayload.class);
        } else if (msg instanceof byte[] bytes) {
            if (bytes.length == 0) {
                return null;
            }
            return objectMapper.readValue(bytes, EmailDispatchPayload.class);
        } else {
            return objectMapper.convertValue(msg, EmailDispatchPayload.class);
        }
    }
}
