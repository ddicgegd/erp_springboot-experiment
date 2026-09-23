package com.ddicg.erp.modules.notification.service;

import com.ddicg.erp.modules.notification.config.NotificationEmailConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTemplateIntegrationTest {

    @Test
    @DisplayName("Nạp trực tiếp template HTML từ package com.ddicg.erp.modules.notification.resources.templates")
    void testDirectTemplateResolutionFromNotificationPackage() {
        NotificationEmailConfig config = new NotificationEmailConfig();
        ClassLoaderTemplateResolver resolver = config.notificationTemplateResolver();

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.addTemplateResolver(resolver);

        Context context = new Context();
        context.setVariables(Map.of(
                "subject", "Xác thực tài khoản ERP",
                "username", "NguyenVanA",
                "verificationUrl", "https://erp.annoeye.com/verify?token=XYZ123"
        ));

        String rendered = engine.process("mail/verification-email", context);

        assertNotNull(rendered);
        assertTrue(rendered.contains("Xác thực tài khoản ERP"));
        assertTrue(rendered.contains("NguyenVanA"));
        assertTrue(rendered.contains("https://erp.annoeye.com/verify?token=XYZ123"));
    }

    @Test
    @DisplayName("Nạp trực tiếp template khôi phục mật khẩu từ package notification")
    void testDirectAccountRecoveryTemplateResolution() {
        NotificationEmailConfig config = new NotificationEmailConfig();
        ClassLoaderTemplateResolver resolver = config.notificationTemplateResolver();

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.addTemplateResolver(resolver);

        Context context = new Context();
        context.setVariables(Map.of(
                "username", "AdminUser",
                "resetUrl", "https://erp.annoeye.com/reset?token=RECOVERY999"
        ));

        String rendered = engine.process("mail/account-recovery-email", context);

        assertNotNull(rendered);
        assertTrue(rendered.contains("Khôi phục thông tin tài khoản"));
        assertTrue(rendered.contains("AdminUser"));
        assertTrue(rendered.contains("https://erp.annoeye.com/reset?token=RECOVERY999"));
    }
}
