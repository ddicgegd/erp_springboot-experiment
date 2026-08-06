package com.ddicg.erp.service.event.email;

import com.ddicg.erp.domainevent.AccountRecoveryEvent;
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
public class AccountRecoveryListener extends BaseEventListener {

    private final String frontendUrl;

    public AccountRecoveryListener(EmailService emailService,
                                   JwtService jwtService,
                                   UserDetailsServiceImpl userDetailsService,
                                   @Value("${frontend.url}") String frontendUrl) {
        super(emailService, jwtService, userDetailsService);
        this.frontendUrl = frontendUrl;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAccountRecovery(AccountRecoveryEvent body) throws MessagingException {
        try {
            String resetUrl = frontendUrl + "/reset-password?token=" + body.token();
            emailService.sendAccountRecoveryEmail(body.user().getEmail(), body.user().getName(), resetUrl);
            log.info("Đã gửi email khôi phục tài khoản cho user: {}", body.user().getName());
        } catch (MessagingException e) {
            log.error("Lỗi gửi email khôi phục tài khoản cho {}: {}", body.user().getEmail(), e.getMessage(), e);
            throw e;
        }
    }
}
