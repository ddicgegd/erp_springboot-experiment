package com.ddicg.erp.modules.notification.kafka.consumer;

import com.ddicg.erp.modules.notification.dto.EmailDeliveryResult;
import com.ddicg.erp.modules.notification.dto.EmailDispatchPayload;
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
    @DisplayName("processEmailDispatch - chặn gửi và trả về DROPPED_DUPLICATE khi phát hiện trùng lặp")
    void testProcessEmailDispatch_DuplicateBlocked() {
        EmailDispatchPayload payload = EmailDispatchPayload.builder()
                .messageId("msg-200")
                .recipient("test@example.com")
                .deduplicationKey("DUPLICATE-KEY")
                .build();

        when(emailProtectionService.acquireDeduplicationLock("DUPLICATE-KEY", 10)).thenReturn(false);

        EmailDeliveryResult result = consumer.processEmailDispatch(payload);

        assertEquals(EmailDeliveryResult.Status.DROPPED_DUPLICATE, result.getStatus());
        verifyNoInteractions(emailSenderService);
    }

    @Test
    @DisplayName("processEmailDispatch - chặn gửi và trả về DROPPED_RATE_LIMITED khi vượt ngưỡng spam")
    void testProcessEmailDispatch_RateLimited() {
        EmailDispatchPayload payload = EmailDispatchPayload.builder()
                .messageId("msg-300")
                .recipient("spammer@example.com")
                .deduplicationKey("KEY-300")
                .build();

        when(emailProtectionService.acquireDeduplicationLock("KEY-300", 10)).thenReturn(true);
        when(emailProtectionService.allowDeliveryRate("spammer@example.com", 5)).thenReturn(false);

        EmailDeliveryResult result = consumer.processEmailDispatch(payload);

        assertEquals(EmailDeliveryResult.Status.DROPPED_RATE_LIMITED, result.getStatus());
        verifyNoInteractions(emailSenderService);
    }
}
