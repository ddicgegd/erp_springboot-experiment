package com.ddicg.erp.modules.notification.kafka.consumer;

import com.ddicg.erp.modules.notification.dto.EmailDeliveryResult;
import com.ddicg.erp.modules.notification.dto.EmailDispatchPayload;
import com.ddicg.erp.modules.notification.exception.EmailDeliveryException;
import jakarta.mail.MessagingException;
import com.ddicg.erp.modules.notification.service.EmailProtectionService;
import com.ddicg.erp.modules.notification.service.EmailSenderService;
import com.ddicg.erp.modules.notification.service.EmailTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private EmailProtectionService emailProtectionService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private EmailSenderService emailSenderService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @InjectMocks
    private NotificationEventConsumer consumer;

    @Test
    @DisplayName("processEmailDispatch - thực thi gửi email thành công khi hợp lệ mọi điều kiện")
    void testProcessEmailDispatch_Success() throws Exception {
        EmailDispatchPayload payload = EmailDispatchPayload.builder()
                .messageId("msg-100")
                .recipient("test@example.com")
                .templateCode("VERIFICATION_EMAIL")
                .deduplicationKey("KEY-100")
                .params(Map.of("username", "testuser"))
                .build();

        when(emailProtectionService.acquireDeduplicationLock("KEY-100", 10)).thenReturn(true);
        when(emailProtectionService.allowDeliveryRate("test@example.com", 5)).thenReturn(true);
        when(emailTemplateService.renderHtml(eq("VERIFICATION_EMAIL"), anyMap())).thenReturn("<html>Rendered</html>");
        when(emailTemplateService.resolveDefaultSubject("VERIFICATION_EMAIL")).thenReturn("Tiêu đề xác thực");

        EmailDeliveryResult result = consumer.processEmailDispatch(payload);

        assertEquals(EmailDeliveryResult.Status.SUCCESS, result.getStatus());
        assertEquals("msg-100", result.getMessageId());
        verify(emailSenderService).sendHtmlEmail("test@example.com", "Tiêu đề xác thực", "<html>Rendered</html>");
    }

    @Test
    void testProcessEmailDispatch_DuplicateBlocked() {
        EmailDispatchPayload payload = EmailDispatchPayload.builder()
                .messageId("msg-200")
                .recipient("test@example.com")
                .deduplicationKey("DUPLICATE-KEY")
                .build();

        when(emailProtectionService.allowDeliveryRate("test@example.com", 5)).thenReturn(true);
        when(emailProtectionService.acquireDeduplicationLock("DUPLICATE-KEY", 10)).thenReturn(false);

        EmailDeliveryResult result = consumer.processEmailDispatch(payload);

        assertEquals(EmailDeliveryResult.Status.DROPPED_DUPLICATE, result.getStatus());
        verifyNoInteractions(emailSenderService);
    }

    @Test
    @DisplayName("processEmailDispatch - chặn gửi và trả về DROPPED_RATE_LIMITED khi vượt ngưỡng spam mà không chiếm dedup lock")
    void testProcessEmailDispatch_RateLimited() {
        EmailDispatchPayload payload = EmailDispatchPayload.builder()
                .messageId("msg-300")
                .recipient("spammer@example.com")
                .deduplicationKey("KEY-300")
                .build();

        when(emailProtectionService.allowDeliveryRate("spammer@example.com", 5)).thenReturn(false);

        EmailDeliveryResult result = consumer.processEmailDispatch(payload);

        assertEquals(EmailDeliveryResult.Status.DROPPED_RATE_LIMITED, result.getStatus());
        verify(emailProtectionService, never()).acquireDeduplicationLock(anyString(), anyLong());
        verifyNoInteractions(emailSenderService);
    }

    @Test
    @DisplayName("processEmailDispatch - áp dụng TTL 365 ngày cho Birthday Email deduplication")
    void testProcessEmailDispatch_BirthdayTtl() throws Exception {
        EmailDispatchPayload payload = EmailDispatchPayload.builder()
                .messageId("msg-bday-1")
                .recipient("birthday@example.com")
                .templateCode("BIRTHDAY_GREETING")
                .deduplicationKey("BIRTHDAY:birthday@example.com:2026")
                .params(Map.of("username", "bdayUser"))
                .build();

        when(emailProtectionService.allowDeliveryRate("birthday@example.com", 5)).thenReturn(true);
        when(emailProtectionService.acquireDeduplicationLock(eq("BIRTHDAY:birthday@example.com:2026"), eq(525600L))).thenReturn(true);
        when(emailTemplateService.renderHtml(eq("BIRTHDAY_GREETING"), anyMap())).thenReturn("<html>Happy Birthday</html>");
        when(emailTemplateService.resolveDefaultSubject("BIRTHDAY_GREETING")).thenReturn("Chúc mừng sinh nhật!");

        EmailDeliveryResult result = consumer.processEmailDispatch(payload);

        assertEquals(EmailDeliveryResult.Status.SUCCESS, result.getStatus());
        verify(emailProtectionService).acquireDeduplicationLock("BIRTHDAY:birthday@example.com:2026", 525600L);
    }

    @Test
    @DisplayName("processEmailDispatch - giải phóng deduplication lock và ném EmailDeliveryException khi SMTP thất bại")
    void testProcessEmailDispatch_SmtpFailureReleasesLockAndThrows() throws Exception {
        EmailDispatchPayload payload = EmailDispatchPayload.builder()
                .messageId("msg-fail-1")
                .recipient("fail@example.com")
                .templateCode("VERIFICATION_EMAIL")
                .deduplicationKey("KEY-FAIL-1")
                .params(Map.of("username", "failUser"))
                .build();

        when(emailProtectionService.allowDeliveryRate("fail@example.com", 5)).thenReturn(true);
        when(emailProtectionService.acquireDeduplicationLock("KEY-FAIL-1", 10)).thenReturn(true);
        when(emailTemplateService.renderHtml(eq("VERIFICATION_EMAIL"), anyMap())).thenReturn("<html>Content</html>");
        when(emailTemplateService.resolveDefaultSubject("VERIFICATION_EMAIL")).thenReturn("Subject");
        doThrow(new MessagingException("SMTP Connection refused")).when(emailSenderService).sendHtmlEmail(anyString(), anyString(), anyString());

        assertThrows(EmailDeliveryException.class, () -> consumer.processEmailDispatch(payload));
        verify(emailProtectionService).releaseDeduplicationLock("KEY-FAIL-1");
    }

    @Test
    @DisplayName("consume - xử lý thành công khi Spring Kafka truyền ConsumerRecord chứa payload JSON")
    void testConsume_WithConsumerRecord() throws Exception {
        String json = "{\"messageId\":\"msg-cr-1\",\"recipient\":\"test@example.com\",\"templateCode\":\"VERIFICATION_EMAIL\",\"deduplicationKey\":\"dedup-cr-1\",\"params\":{\"username\":\"crUser\"}}";
        org.apache.kafka.clients.consumer.ConsumerRecord<String, Object> record =
                new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        com.ddicg.erp.core.common.constants.KafkaTopics.NOTIFICATION_EMAIL_TOPIC, 0, 0L, "test@example.com", json);

        when(emailProtectionService.acquireDeduplicationLock(eq("dedup-cr-1"), eq(10L))).thenReturn(true);
        when(emailProtectionService.allowDeliveryRate(anyString(), anyInt())).thenReturn(true);
        when(emailTemplateService.renderHtml(anyString(), anyMap())).thenReturn("<html>Ok</html>");
        when(emailTemplateService.resolveDefaultSubject(anyString())).thenReturn("Subject");

        assertDoesNotThrow(() -> consumer.consume(record, "test@example.com"));
        verify(emailSenderService).sendHtmlEmail(eq("test@example.com"), eq("Subject"), eq("<html>Ok</html>"));
    }
}
