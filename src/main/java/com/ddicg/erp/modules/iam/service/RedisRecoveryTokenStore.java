package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisRecoveryTokenStore implements RecoveryTokenStore {

  private final RedisService redisService;

  @Override
  public Optional<String> findTokenByEmail(String email) {
    return Optional.ofNullable((String) redisService.getValue(RedisTable.AUTH_RECOVERY_EMAIL, email));
  }

  @Override
  public Optional<String> findEmailByToken(String token) {
    String tokenHash = hashToken(token);
    return Optional.ofNullable((String) redisService.getValue(RedisTable.AUTH_RECOVERY_TOKEN, tokenHash));
  }

  @Override
  public void save(String email, String token, Duration ttl) {
    long seconds = ttl.toSeconds();
    // Token Rotation: If email already has an existing token, revoke it first
    String existingTokenHash = (String) redisService.getValue(RedisTable.AUTH_RECOVERY_EMAIL, email);
    if (existingTokenHash != null) {
      redisService.delete(RedisTable.AUTH_RECOVERY_TOKEN, existingTokenHash);
      redisService.delete(RedisTable.AUTH_RECOVERY_EMAIL, email);
    }

    String tokenHash = hashToken(token);
    redisService.setValueWithExpiry(RedisTable.AUTH_RECOVERY_TOKEN, tokenHash, email, seconds, TimeUnit.SECONDS);
    redisService.setValueWithExpiry(RedisTable.AUTH_RECOVERY_EMAIL, email, tokenHash, seconds, TimeUnit.SECONDS);
  }

  @Override
  public void extend(String email, String token, Duration ttl) {
    long seconds = ttl.toSeconds();
    String tokenHash = hashToken(token);
    redisService.expire(RedisTable.AUTH_RECOVERY_TOKEN, tokenHash, seconds, TimeUnit.SECONDS);
    redisService.expire(RedisTable.AUTH_RECOVERY_EMAIL, email, seconds, TimeUnit.SECONDS);
  }

  @Override
  public void consume(String email, String token) {
    String tokenHash = hashToken(token);
    redisService.delete(RedisTable.AUTH_RECOVERY_TOKEN, tokenHash);
    redisService.delete(RedisTable.AUTH_RECOVERY_EMAIL, email);
  }

  private String hashToken(String token) {
    if (token == null) {
      return null;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Thuật toán SHA-256 không khả dụng trên hệ thống", e);
    }
  }
}
