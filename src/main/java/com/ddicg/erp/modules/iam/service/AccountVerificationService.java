package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountVerificationService {

  private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofMinutes(15);

  private final UserRepository userRepository;
  private final VerificationTokenStore verificationTokenStore;

  public VerificationToken issue(String email) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Người dùng không tồn tại"));

    if (user.getStatus() == ActiveStatus.LOCKED) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Tài khoản đang bị khóa.");
    }

    if (user.getStatus() == ActiveStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Tài khoản đã được kích hoạt trước đó.");
    }

    String newToken = UUID.randomUUID().toString();
    verificationTokenStore.save(email, newToken, VERIFICATION_TOKEN_TTL);
    log.info("Cấp token xác thực email mới cho user: {} với TTL: {} phút", user.getUsername(), VERIFICATION_TOKEN_TTL.toMinutes());

    return new VerificationToken(user, newToken, email);
  }

  @Transactional
  public VerificationToken verify(String token) {
    if (!StringUtils.hasText(token)) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Mã xác thực email không hợp lệ hoặc đã hết hạn.");
    }

    String trimmedToken = token.trim();
    String email = verificationTokenStore.findEmailByToken(trimmedToken)
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS,
            "Mã xác thực email không hợp lệ hoặc đã hết hạn."));
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
            "Người dùng không tồn tại để xác thực."));

    if (user.getStatus() == ActiveStatus.LOCKED) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Tài khoản đang bị khóa.");
    }

    if (user.getStatus() == ActiveStatus.INACTIVE) {
      user.setStatus(ActiveStatus.ACTIVE);
      userRepository.save(user);
      log.info("Xác thực email và kích hoạt tài khoản thành công cho user: {}", user.getUsername());
    }

    verificationTokenStore.consume(email, trimmedToken);
    return new VerificationToken(user, trimmedToken, email);
  }

  public void consume(VerificationToken verificationToken) {
    verificationTokenStore.consume(verificationToken.email(), verificationToken.token());
  }
}
