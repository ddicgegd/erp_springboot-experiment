package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.modules.notification.kafka.producer.NotificationEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Adapter tương thích ngược cho các class cũ trong IAM.
 * @deprecated Khuyến nghị chuyển sang sử dụng trực tiếp {@link NotificationEventProducer}.
 */
@Slf4j
@Deprecated
@Service
@RequiredArgsConstructor
public class EmailService {

    private final NotificationEventProducer notificationEventProducer;

    public void sendVerificationEmail(String to, String username, String verificationUrl) {
        notificationEventProducer.sendVerificationEmail(to, username, verificationUrl, null);
    }

    public void sendAccountRecoveryEmail(String to, String username, String resetUrl) {
        notificationEventProducer.sendAccountRecoveryEmail(to, username, resetUrl, null);
    }
}
