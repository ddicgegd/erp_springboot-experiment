package com.ddicg.erp.core.event.email.email;

import com.ddicg.erp.core.event.domainevent.AccountRecoveryEvent;
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
public class AccountRecoveryListener extends BaseEventListener {

    private final String frontendUrl;
    private final NotificationEventProducer notificationEventProducer;

    public AccountRecoveryListener(EmailService emailService,
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
    public void handleAccountRecovery(AccountRecoveryEvent body) {
        try {
            String resetUrl = frontendUrl + "/reset-password?token=" + body.token();
            notificationEventProducer.sendAccountRecoveryEmail(
                    body.user().getEmail(),
                    body.user().getName(),
                    resetUrl,
                    body.token()
            );
            log.info("Đã phát Kafka event khôi phục tài khoản cho user: {}", body.user().getName());
        } catch (Exception e) {
            log.error("Lỗi phát Kafka event khôi phục tài khoản cho {}: {}", body.user().getEmail(), e.getMessage(), e);
        }
    }
}