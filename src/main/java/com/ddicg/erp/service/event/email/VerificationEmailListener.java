package com.ddicg.erp.service.event.email;

import com.ddicg.erp.domainevent.VerificationEmailEvent;
import com.ddicg.erp.service.EmailService;
import com.ddicg.erp.service.JwtService;
import com.ddicg.erp.service.UserDetails.UserDetailsServiceImpl;
import com.ddicg.erp.service.event.base.BaseEventListener;
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
