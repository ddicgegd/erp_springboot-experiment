package com.ddicg.erp.core.event.email.email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.core.event.domainevent.VerificationEmailEvent;
import com.ddicg.erp.modules.iam.service.EmailService;
import com.ddicg.erp.modules.iam.service.JwtService;
import com.ddicg.erp.modules.iam.service.UserDetailsServiceImpl;
import com.ddicg.erp.core.event.email.base.BaseEventListener;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class VerificationEmailListener extends BaseEventListener {
    private static final Logger log = LoggerFactory.getLogger(VerificationEmailListener.class);


    private final String frontendUrl;

    public VerificationEmailListener(EmailService emailService,
                                     JwtService jwtService,
                                     UserDetailsServiceImpl userDetailsService,
                                     @Value("${frontend.url}") String frontendUrl) {
        super(emailService, jwtService, userDetailsService);
        this.frontendUrl = frontendUrl;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVerificationEmail(VerificationEmailEvent body) {
        try {
            String verificationUrl = frontendUrl + "/verify-email?token="
                    + body.emailVerificationToken();

            emailService.sendVerificationEmail(
                    body.email(),
                    body.username(),
                    verificationUrl
            );
        } catch (MessagingException e) {
            log.error("Gửi email xác thực thất bại cho user: {}", body.username(), e);
        }
    }
}