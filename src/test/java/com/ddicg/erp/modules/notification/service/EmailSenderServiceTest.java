package com.ddicg.erp.modules.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSenderServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    @DisplayName("sendHtmlEmail - gửi email thành công với MimeMessage")
    void testSendHtmlEmail_Success() throws MessagingException {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        EmailSenderServiceImpl service = new EmailSenderServiceImpl(mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "sender@example.com");

        assertDoesNotThrow(() ->
                service.sendHtmlEmail("receiver@example.com", "Tiêu đề test", "<h1>Nội dung HTML</h1>")
        );

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendHtmlEmail - xử lý an toàn khi JavaMailSender là null")
    void testSendHtmlEmail_NullSender_Safe() {
        EmailSenderServiceImpl service = new EmailSenderServiceImpl(null);

        assertDoesNotThrow(() ->
                service.sendHtmlEmail("receiver@example.com", "Tiêu đề", "<p>Test</p>")
        );
    }
}
