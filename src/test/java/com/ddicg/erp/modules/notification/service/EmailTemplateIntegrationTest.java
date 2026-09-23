package com.ddicg.erp.modules.notification.service;

import com.ddicg.erp.modules.notification.config.NotificationEmailConfig;
import com.ddicg.erp.modules.notification.dto.TemplateCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Test kiểm thử trực tiếp quá trình biên dịch (render) các file template HTML thực tế
 * bằng SpringTemplateEngine thật (không mock) để bảo đảm cú pháp Thymeleaf và cấu trúc DOM chuẩn xác.
 */
class EmailTemplateIntegrationTest {

    private EmailTemplateServiceImpl emailTemplateService;

    @BeforeEach
    void setUp() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        NotificationEmailConfig config = new NotificationEmailConfig();
        templateEngine.setTemplateResolver(config.notificationTemplateResolver());

        emailTemplateService = new EmailTemplateServiceImpl(templateEngine);
    }

    @Test
    @DisplayName("Biên dịch thực tế template VERIFICATION_EMAIL với đầy đủ biến")
    void testRealRender_VerificationEmail_Success() {
        Map<String, String> variables = Map.of(
                "subject", "Xác thực tài khoản của bạn",
                "username", "nguyenvana",
                "email", "nguyenvana@gmail.com",
                "token", "SECURE_TOKEN_XYZ",
                "expiryMinutes", "5",
                "verificationUrl", "https://erp.annoeye.com/verify-email?token=SECURE_TOKEN_XYZ"
        );

        String html = emailTemplateService.renderHtml(TemplateCode.VERIFICATION_EMAIL.name(), variables);

        assertNotNull(html, "HTML kết quả không được null");
        assertFalse(html.isBlank(), "HTML kết quả không được rỗng");

        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("target/rendered-emails");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve("verification-preview.html"), html);
        } catch (Exception ignored) {}

        // Kiểm tra biến người dùng và liên kết hành động
        assertTrue(html.contains("nguyenvana"), "HTML phải chứa username được truyền vào");
        assertTrue(html.contains("nguyenvana@gmail.com"), "HTML phải chứa email người dùng");
        assertTrue(html.contains("https://erp.annoeye.com/verify-email?token=SECURE_TOKEN_XYZ"), "HTML phải chứa link verify");

        // Kiểm tra nhận diện thương hiệu ANNOEYE ERP
        assertTrue(html.contains("ANNOEYE"), "HTML phải chứa thương hiệu ANNOEYE");
        assertTrue(html.contains("Enterprise Security"), "HTML phải chứa subtitle");

        // Kiểm tra các đặc tả của Bevel & shadcn/ui
        assertTrue(html.contains("btn-bevel"), "HTML phải chứa class bevel button");
        assertTrue(html.contains("#FF4D24"), "HTML phải chứa mã màu cam thương hiệu #FF4D24");
        assertTrue(html.contains("KÍCH HOẠT TÀI KHOẢN"), "HTML phải chứa nhãn nút kích hoạt");
        assertTrue(html.contains("Xác thực tài khoản"), "HTML phải chứa tiêu đề chính");
        assertTrue(html.contains("Xác thực tài khoản <span"), "HTML phải chứa tiêu đề với tên tài khoản");
        assertTrue(html.contains("SECURE_TOKEN_XYZ"), "HTML phải chứa token xác thực");
        assertTrue(html.contains("copy-token-box"), "HTML phải chứa khung sao chép token");
    }

    @Test
    @DisplayName("Biên dịch thực tế template VERIFICATION_EMAIL với biến subject bị khuyết (kiểm tra Elvis fallback)")
    void testRealRender_VerificationEmail_FallbackSubject() {
        Map<String, String> variables = Map.of(
                "username", "lethic",
                "verificationUrl", "https://erp.annoeye.com/verify-email?token=FALLBACK_TOKEN"
        );

        String html = emailTemplateService.renderHtml(TemplateCode.VERIFICATION_EMAIL.name(), variables);

        assertNotNull(html);
        assertTrue(html.contains("lethic"));
        assertTrue(html.contains("Xác thực tài khoản"));
    }

    @Test
    @DisplayName("Biên dịch thực tế template ACCOUNT_RECOVERY với đầy đủ biến")
    void testRealRender_AccountRecovery_Success() {
        Map<String, String> variables = Map.of(
                "subject", "Khôi phục thông tin tài khoản",
                "username", "tranvanb",
                "email", "tranvanb@gmail.com",
                "token", "RECOVERY_TOKEN_ABC",
                "expiryMinutes", "10",
                "resetUrl", "https://erp.annoeye.com/reset-password?token=RECOVERY_TOKEN_ABC"
        );

        String html = emailTemplateService.renderHtml(TemplateCode.ACCOUNT_RECOVERY.name(), variables);

        assertNotNull(html, "HTML kết quả không được null");
        assertFalse(html.isBlank(), "HTML kết quả không được rỗng");

        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("target/rendered-emails");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve("account-recovery-preview.html"), html);
        } catch (Exception ignored) {}

        // Kiểm tra biến người dùng và liên kết khôi phục
        assertTrue(html.contains("tranvanb"), "HTML phải chứa username khôi phục");
        assertTrue(html.contains("tranvanb@gmail.com"), "HTML phải chứa email người dùng");
        assertTrue(html.contains("https://erp.annoeye.com/reset-password?token=RECOVERY_TOKEN_ABC"), "HTML phải chứa resetUrl");

        // Kiểm tra nhận diện thương hiệu ANNOEYE ERP
        assertTrue(html.contains("ANNOEYE"), "HTML phải chứa thương hiệu ANNOEYE");
        assertTrue(html.contains("Enterprise Security"), "HTML phải chứa subtitle");

        // Kiểm tra các đặc tả của Bevel & shadcn/ui
        assertTrue(html.contains("btn-bevel"), "HTML phải chứa class bevel button");
        assertTrue(html.contains("#FF4D24"), "HTML phải chứa mã màu cam thương hiệu #FF4D24");
        assertTrue(html.contains("ĐẶT LẠI MẬT KHẨU"), "HTML phải chứa nhãn nút đặt lại mật khẩu");
        assertTrue(html.contains("Khôi phục tài khoản"), "HTML phải chứa tiêu đề chính");
        assertTrue(html.contains("RECOVERY_TOKEN_ABC"), "HTML phải chứa token khôi phục");
        assertTrue(html.contains("copy-token-box"), "HTML phải chứa khung sao chép token");
        assertTrue(html.contains("10 phút"), "HTML phải chứa thời hạn 10 phút");
    }

    @Test
    @DisplayName("Biên dịch thực tế template ACCOUNT_RECOVERY với biến subject bị khuyết")
    void testRealRender_AccountRecovery_FallbackSubject() {
        Map<String, String> variables = Map.of(
                "username", "phamvand",
                "resetUrl", "https://erp.annoeye.com/reset-password?token=TOKEN_DEF"
        );

        String html = emailTemplateService.renderHtml(TemplateCode.ACCOUNT_RECOVERY.name(), variables);

        assertNotNull(html);
        assertTrue(html.contains("phamvand"));
        assertTrue(html.contains("Khôi phục thông tin tài khoản"));
    }
}
