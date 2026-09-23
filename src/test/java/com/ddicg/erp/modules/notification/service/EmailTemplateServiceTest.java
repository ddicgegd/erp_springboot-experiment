package com.ddicg.erp.modules.notification.service;

import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.modules.notification.dto.TemplateCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailTemplateServiceTest {

    @Mock
    private SpringTemplateEngine templateEngine;

    @InjectMocks
    private EmailTemplateServiceImpl emailTemplateService;

    @Test
    @DisplayName("renderHtml - biên dịch template VERIFICATION_EMAIL thành công")
    void testRenderHtml_VerificationEmail() {
        when(templateEngine.process(eq("mail/verification-email"), any(Context.class)))
                .thenReturn("<html><body>Mock Verification Email</body></html>");

        Map<String, String> variables = Map.of(
                "username", "testuser",
                "verificationUrl", "http://localhost/verify"
        );

        String html = emailTemplateService.renderHtml("VERIFICATION_EMAIL", variables);
        assertNotNull(html);
        assertTrue(html.contains("Mock Verification Email"));
        verify(templateEngine).process(eq("mail/verification-email"), any(Context.class));
    }

    @Test
    @DisplayName("renderHtml - biên dịch template ACCOUNT_RECOVERY thành công")
    void testRenderHtml_AccountRecovery() {
        when(templateEngine.process(eq("mail/account-recovery-email"), any(Context.class)))
                .thenReturn("<html><body>Mock Recovery Email</body></html>");

        Map<String, String> variables = Map.of(
                "username", "testuser",
                "resetUrl", "http://localhost/reset"
        );

        String html = emailTemplateService.renderHtml(TemplateCode.ACCOUNT_RECOVERY.name(), variables);
        assertNotNull(html);
        assertTrue(html.contains("Mock Recovery Email"));
        verify(templateEngine).process(eq("mail/account-recovery-email"), any(Context.class));
    }

    @Test
    @DisplayName("renderHtml - ném BusinessException khi templateCode không hợp lệ")
    void testRenderHtml_InvalidTemplateCode() {
        assertThrows(BusinessException.class, () -> emailTemplateService.renderHtml(null, Map.of()));
        assertThrows(BusinessException.class, () -> emailTemplateService.renderHtml("   ", Map.of()));
    }

    @Test
    @DisplayName("resolveDefaultSubject - trả về đúng tiêu đề mặc định của từng template")
    void testResolveDefaultSubject() {
        assertEquals("Xác thực tài khoản", emailTemplateService.resolveDefaultSubject("VERIFICATION_EMAIL"));
        assertEquals("Khôi phục thông tin tài khoản", emailTemplateService.resolveDefaultSubject("ACCOUNT_RECOVERY"));
        assertEquals("Thông báo từ hệ thống ERP", emailTemplateService.resolveDefaultSubject("UNKNOWN_TEMPLATE"));
    }
}
