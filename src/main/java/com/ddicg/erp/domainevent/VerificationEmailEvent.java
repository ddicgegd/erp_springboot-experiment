package com.ddicg.erp.domainevent;

import com.ddicg.erp.model.enums.ActiveStatus;
import lombok.Builder;

@Builder
public record VerificationEmailEvent(String email, String username, String emailVerificationToken, ActiveStatus purpose) {}

