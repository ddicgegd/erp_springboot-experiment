package com.ddicg.erp.service.accountrecovery;

import com.ddicg.erp.model.entity.User;

public record RecoveryToken(User user, String token, String email) {
}
