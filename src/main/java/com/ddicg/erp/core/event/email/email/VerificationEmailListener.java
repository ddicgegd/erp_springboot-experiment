package com.ddicg.erp.core.event.email.email;

import com.ddicg.erp.core.event.domainevent.VerificationEmailEvent;
import com.ddicg.erp.core.event.email.base.BaseEventListener;
import com.ddicg.erp.modules.iam.service.EmailService;
import com.ddicg.erp.modules.iam.service.JwtService;
import com.ddicg.erp.modules.iam.service.UserDetailsServiceImpl;
import com.ddicg.erp.modules.notification.kafka.producer.NotificationEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class VerificationEmailListener extends BaseEventListener {

    private final String frontendUrl;
    private final NotificationEventProducer notificationEventProducer;

    public VerificationEmailListener(EmailService emailService,
                                     JwtService jwtService,
                                     UserDetailsServiceImpl userDetailsService,
                                     NotificationEventProducer notificationEventProducer,
                                     @Value("${frontend.url}") String frontendUrl) {
        super(emailService, jwtService, userDetailsService);
        this.notificationEventProducer = notificationEventProducer;
        this.frontendUrl = frontendUrl;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVerificationEmail(VerificationEmailEvent body) {
        try {
            String verificationUrl = frontendUrl + "/verify-email?token="
                    + body.emailVerificationToken();

            notificationEventProducer.sendVerificationEmail(
                    body.email(),
                    body.username(),
                    verificationUrl,
                    body.emailVerificationToken()
            );
            log.info("Đã phát Kafka event xác thực email cho user: {}", body.username());
        } catch (Exception e) {
            log.error("Gửi Kafka event xác thực email thất bại cho user: {}", body.username(), e);
        }
    }
}