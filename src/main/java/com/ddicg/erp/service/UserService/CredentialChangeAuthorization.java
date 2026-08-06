package com.ddicg.erp.service.UserService;

import com.ddicg.erp.model.entity.User;
import com.ddicg.erp.service.accountrecovery.AccountRecoveryService;
import com.ddicg.erp.service.accountrecovery.RecoveryToken;
import com.ddicg.erp.util.SecurityUtil;
import com.ddicg.erp.web.rest.error.BusinessException;
import com.ddicg.erp.web.rest.error.ErrorCode;
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
      return resolveFromRecoveryToken(token);
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
