package com.ddicg.erp.modules.iam.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private String processHtmlTemplate(String templateName, Map<String, Object> variable) {
        Context context = new Context();
        if (variable != null) variable.forEach(context::setVariable);
        return templateEngine.process("/mail/" + templateName, context);
    }

    @Async
    public void sendVerificationEmail(String to, String username, String verificationUrl) throws MessagingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject("Xác thực tài khoản");

        Map<String, Object> variables = Map.of("subject", "Xác thực tài khoản", "username", username, "verificationUrl", verificationUrl);
        String htmlContent = processHtmlTemplate("verification-email.html", variables);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }

    @Async
    public void sendAccountRecoveryEmail(String to, String username, String resetUrl) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject("Khôi phục thông tin tài khoản");

        Map<String, Object> variables = Map.of("username", username, "resetUrl", resetUrl);

        String htmlContent = processHtmlTemplate("account-recovery-email.html", variables);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }
}
