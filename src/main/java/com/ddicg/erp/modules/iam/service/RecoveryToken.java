package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.modules.iam.model.User;

public record RecoveryToken(User user, String token, String email) {
}
