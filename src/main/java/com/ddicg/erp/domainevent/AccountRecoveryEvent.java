package com.ddicg.erp.domainevent;

import com.ddicg.erp.model.entity.User;
import lombok.Builder;

@Builder
public record AccountRecoveryEvent(User user, String token) {}
