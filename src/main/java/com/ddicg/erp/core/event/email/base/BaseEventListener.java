package com.ddicg.erp.core.event.email.base;

import com.ddicg.erp.modules.iam.service.EmailService;
import com.ddicg.erp.modules.iam.service.JwtService;
import com.ddicg.erp.modules.iam.service.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Value;

public abstract class BaseEventListener {
    protected final EmailService emailService;
    protected final JwtService jwtService;
    protected final UserDetailsServiceImpl userDetailsService;

    @Value("${server.port}")
    protected String serverPort;

    public BaseEventListener(EmailService emailService, JwtService jwtService, UserDetailsServiceImpl userDetailsService) {
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }
}
