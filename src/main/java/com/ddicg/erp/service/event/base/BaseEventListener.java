package com.ddicg.erp.service.event.base;

import com.ddicg.erp.service.EmailService;
import com.ddicg.erp.service.JwtService;
import com.ddicg.erp.service.UserDetails.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

@RequiredArgsConstructor
public abstract class BaseEventListener {
    protected final EmailService emailService;
    protected final JwtService jwtService;
    protected final UserDetailsServiceImpl userDetailsService;

    @Value("${server.port}")
    protected String serverPort;
}
