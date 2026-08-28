package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
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
    return Optional.ofNullable((String) redisService.getValue(RedisTable.AUTH_RECOVERY_TOKEN, token));
  }

  @Override
  public void save(String email, String token, Duration ttl) {
    long seconds = ttl.toSeconds();
    redisService.setValueWithExpiry(RedisTable.AUTH_RECOVERY_TOKEN, token, email, seconds, TimeUnit.SECONDS);
    redisService.setValueWithExpiry(RedisTable.AUTH_RECOVERY_EMAIL, email, token, seconds, TimeUnit.SECONDS);
  }

  @Override
  public void extend(String email, String token, Duration ttl) {
    long seconds = ttl.toSeconds();
    redisService.expire(RedisTable.AUTH_RECOVERY_TOKEN, token, seconds, TimeUnit.SECONDS);
    redisService.expire(RedisTable.AUTH_RECOVERY_EMAIL, email, seconds, TimeUnit.SECONDS);
  }

  @Override
  public void consume(String email, String token) {
    redisService.delete(RedisTable.AUTH_RECOVERY_TOKEN, token);
    redisService.delete(RedisTable.AUTH_RECOVERY_EMAIL, email);
  }
}
