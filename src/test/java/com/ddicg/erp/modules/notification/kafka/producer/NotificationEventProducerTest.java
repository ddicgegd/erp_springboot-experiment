package com.ddicg.erp.modules.notification.kafka.producer;

import com.ddicg.erp.core.common.constants.KafkaTopics;
import com.ddicg.erp.modules.notification.dto.EmailDispatchPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @InjectMocks
    private NotificationEventProducer producer;

    @Test
    @DisplayName("sendVerificationEmail - phát event đúng topic và chứa đầy đủ dữ liệu")
    void testSendVerificationEmail() throws Exception {
        producer.sendVerificationEmail("user@example.com", "user1", "http://verify.url", "token123");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.NOTIFICATION_EMAIL_TOPIC), eq("user@example.com"), messageCaptor.capture());

        String json = messageCaptor.getValue();
        EmailDispatchPayload payload = objectMapper.readValue(json, EmailDispatchPayload.class);

        assertEquals("user@example.com", payload.getRecipient());
        assertEquals("VERIFICATION_EMAIL", payload.getTemplateCode());
        assertEquals("VERIFY:user@example.com:token123", payload.getDeduplicationKey());
        assertEquals("user1", payload.getParams().get("username"));
        assertEquals("http://verify.url", payload.getParams().get("verificationUrl"));
        assertEquals("15", payload.getParams().get("expiryMinutes"));
    }

    @Test
    @DisplayName("sendAccountRecoveryEmail - phát event đúng topic và chứa deduplicationKey chính xác")
    void testSendAccountRecoveryEmail() throws Exception {
        producer.sendAccountRecoveryEmail("user@example.com", "user1", "http://reset.url", "tokenXYZ");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.NOTIFICATION_EMAIL_TOPIC), eq("user@example.com"), messageCaptor.capture());

        String json = messageCaptor.getValue();
        EmailDispatchPayload payload = objectMapper.readValue(json, EmailDispatchPayload.class);

        assertEquals("user@example.com", payload.getRecipient());
        assertEquals("ACCOUNT_RECOVERY", payload.getTemplateCode());
        assertEquals("RECOVERY:user@example.com:tokenXYZ", payload.getDeduplicationKey());
        assertEquals("http://reset.url", payload.getParams().get("resetUrl"));
        assertEquals("20", payload.getParams().get("expiryMinutes"));
    }

    @Test
    @DisplayName("dispatchEmail - bỏ qua khi payload bị null")
    void testDispatchEmail_NullPayload() {
        producer.dispatchEmail(null);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("sendCredentialChangeEmail - phát event đúng topic với expiryMinutes 5 phút")
    void testSendCredentialChangeEmail() throws Exception {
        producer.sendCredentialChangeEmail("user@example.com", "user1", "http://activate.url?token=CRED123", "CRED123");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.NOTIFICATION_EMAIL_TOPIC), eq("user@example.com"), messageCaptor.capture());

        String json = messageCaptor.getValue();
        EmailDispatchPayload payload = objectMapper.readValue(json, EmailDispatchPayload.class);

        assertEquals("user@example.com", payload.getRecipient());
        assertEquals("CREDENTIAL_CHANGE", payload.getTemplateCode());
        assertEquals("CREDENTIAL_CHANGE:user@example.com:CRED123", payload.getDeduplicationKey());
        assertEquals("http://activate.url?token=CRED123", payload.getParams().get("activationUrl"));
        assertEquals("5", payload.getParams().get("expiryMinutes"));
    }
}
