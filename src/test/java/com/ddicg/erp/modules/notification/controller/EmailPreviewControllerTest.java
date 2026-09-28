package com.ddicg.erp.modules.notification.controller;

import com.ddicg.erp.modules.notification.dto.EmailPreviewRequest;
import com.ddicg.erp.modules.notification.service.EmailTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailPreviewControllerTest {

    @Mock
    private EmailTemplateService emailTemplateService;

    @InjectMocks
    private EmailPreviewController controller;

    @Test
    @DisplayName("previewByGet - trả về HTTP 200 kèm HTML render thành công")
    void testPreviewByGet() {
        when(emailTemplateService.resolveDefaultSubject("VERIFICATION_EMAIL")).thenReturn("Xác thực tài khoản");
        when(emailTemplateService.renderHtml(eq("VERIFICATION_EMAIL"), anyMap()))
                .thenReturn("<html><body>Mock Preview HTML</body></html>");

        ResponseEntity<String> response = controller.previewByGet("VERIFICATION_EMAIL", "User Demo", "http://preview.url");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Mock Preview HTML"));
    }

    @Test
    @DisplayName("previewByPost - nhận body JSON và trả về HTML preview thành công")
    void testPreviewByPost() {
        when(emailTemplateService.renderHtml(eq("ACCOUNT_RECOVERY"), anyMap()))
                .thenReturn("<html><body>Mock Recovery HTML</body></html>");

        EmailPreviewRequest req = EmailPreviewRequest.builder()
                .templateCode("ACCOUNT_RECOVERY")
                .variables(Map.of("username", "testuser"))
                .build();

        ResponseEntity<String> response = controller.previewByPost(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Mock Recovery HTML"));
    }

    @Test
    @DisplayName("previewByGet - truyền đúng activationUrl và expiryMinutes cho CREDENTIAL_CHANGE")
    void testPreviewByGet_CredentialChange() {
        org.mockito.ArgumentCaptor<Map<String, String>> mapCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        when(emailTemplateService.resolveDefaultSubject("CREDENTIAL_CHANGE")).thenReturn("Xác thực thay đổi thông tin đăng nhập");
        when(emailTemplateService.renderHtml(eq("CREDENTIAL_CHANGE"), mapCaptor.capture()))
                .thenReturn("<html><body>Credential Change HTML</body></html>");

        ResponseEntity<String> response = controller.previewByGet("CREDENTIAL_CHANGE", "User Demo", "http://server.url/activate?token=ABC123XYZ");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, String> capturedVars = mapCaptor.getValue();
        assertEquals("http://server.url/activate?token=ABC123XYZ", capturedVars.get("activationUrl"));
        assertEquals("5", capturedVars.get("expiryMinutes"));
        assertEquals("ABC123XYZ", capturedVars.get("token"));
    }

    @Test
    @DisplayName("previewByGet - truyền đầy đủ voucherCode, discountPercent, giftUrl cho BIRTHDAY_GREETING")
    void testPreviewByGet_BirthdayGreeting() {
        org.mockito.ArgumentCaptor<Map<String, String>> mapCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        when(emailTemplateService.resolveDefaultSubject("BIRTHDAY_GREETING")).thenReturn("Chúc mừng sinh nhật!");
        when(emailTemplateService.renderHtml(eq("BIRTHDAY_GREETING"), mapCaptor.capture()))
                .thenReturn("<html><body>Birthday HTML</body></html>");

        ResponseEntity<String> response = controller.previewByGet("BIRTHDAY_GREETING", "User Birthday", "http://gift.url");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, String> capturedVars = mapCaptor.getValue();
        assertEquals("BDAY-2026-PREVIEW", capturedVars.get("voucherCode"));
        assertEquals("15", capturedVars.get("discountPercent"));
        assertEquals("http://gift.url", capturedVars.get("giftUrl"));
    }
}
