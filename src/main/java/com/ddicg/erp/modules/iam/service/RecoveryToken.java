package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.modules.iam.model.User;

public record RecoveryToken(User user, String token, String email) {
    public boolean isPendingActivation() {
        return user != null && user.getStatus() != ActiveStatus.ACTIVE;
    }
}
