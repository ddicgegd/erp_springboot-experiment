package com.ddicg.erp.modules.iam.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(AccountRecoveryService.class);


  private static final Duration RECOVERY_TOKEN_TTL = Duration.ofHours(24);

  private final UserRepository userRepository;
  private final RecoveryTokenStore recoveryTokenStore;

  public RecoveryToken issue(String email) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Người dùng không tồn tại"));

    if (user.getStatus() != ActiveStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Tài khoản chưa được kích hoạt.");
    }

    String token = recoveryTokenStore.findTokenByEmail(email)
        .map(existingToken -> {
          recoveryTokenStore.extend(email, existingToken, RECOVERY_TOKEN_TTL);
          log.info("Gia hạn token khôi phục cũ cho user: {}", user.getUsername());
          return existingToken;
        })
        .orElseGet(() -> {
          String newToken = UUID.randomUUID().toString();
          recoveryTokenStore.save(email, newToken, RECOVERY_TOKEN_TTL);
          log.info("Tạo token khôi phục mới cho user: {}", user.getUsername());
          return newToken;
        });

    return new RecoveryToken(user, token, email);
  }

  public RecoveryToken resolve(String token) {
    if (!StringUtils.hasText(token)) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Token khôi phục không được để trống.");
    }

    String email = recoveryTokenStore.findEmailByToken(token)
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS,
            "Token khôi phục không hợp lệ hoặc đã hết hạn."));

    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
            "Người dùng không tồn tại để xác thực."));

    return new RecoveryToken(user, token, email);
  }

  public void consume(RecoveryToken recoveryToken) {
    recoveryTokenStore.consume(recoveryToken.email(), recoveryToken.token());
  }
}