package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.service.AccountRecoveryService;
import com.ddicg.erp.modules.iam.service.RecoveryToken;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CredentialChangeAuthorization {

  private final AccountRecoveryService accountRecoveryService;
  private final SecurityUtil securityUtil;

  public Authorization resolveFromRecoveryToken(String token) {
    return Authorization.recovery(accountRecoveryService.resolve(token));
  }

  public Authorization resolveFromRecoveryTokenOrSession(String token) {
    if (StringUtils.hasText(token)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED,
          "Mã khôi phục không có quyền thay đổi tên đăng nhập.");
    }

    User user = securityUtil.getCurrentUser()
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
            "Người dùng chưa đăng nhập hoặc token khôi phục không hợp lệ."));

    return Authorization.session(user);
  }

  public void consumeRecoveryToken(Authorization authorization) {
    if (!authorization.recoveryTokenBased()) {
      return;
    }

    accountRecoveryService.consume(authorization.recoveryToken());
  }

  public void validatePasswordResetPermission(Authorization authorization) {
    // Unactivated accounts are activated during validateResetToken or resetPassword
  }

  public record Authorization(
      User user,
      RecoveryToken recoveryToken,
      boolean recoveryTokenBased) {

    static Authorization recovery(RecoveryToken recoveryToken) {
      return new Authorization(recoveryToken.user(), recoveryToken, true);
    }

    static Authorization session(User user) {
      return new Authorization(user, null, false);
    }
  }
}
