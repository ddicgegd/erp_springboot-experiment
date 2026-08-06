package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisRecoveryTokenStore implements RecoveryTokenStore {

  private static final String TOKEN_PREFIX = "recovery:token:";
  private static final String EMAIL_PREFIX = "recovery:email:";

  private final RedisService redisService;

  @Override
  public Optional<String> findTokenByEmail(String email) {
    return Optional.ofNullable((String) redisService.getValue(emailKey(email)));
  }

  @Override
  public Optional<String> findEmailByToken(String token) {
    return Optional.ofNullable((String) redisService.getValue(tokenKey(token)));
  }

  @Override
  public void save(String email, String token, Duration ttl) {
    long seconds = ttl.toSeconds();
    redisService.setValueWithExpiry(tokenKey(token), email, seconds, TimeUnit.SECONDS);
    redisService.setValueWithExpiry(emailKey(email), token, seconds, TimeUnit.SECONDS);
  }

  @Override
  public void extend(String email, String token, Duration ttl) {
    long seconds = ttl.toSeconds();
    redisService.expire(tokenKey(token), seconds, TimeUnit.SECONDS);
    redisService.expire(emailKey(email), seconds, TimeUnit.SECONDS);
  }

  @Override
  public void consume(String email, String token) {
    redisService.delete(tokenKey(token), emailKey(email));
  }

  private String tokenKey(String token) {
    return TOKEN_PREFIX + token;
  }

  private String emailKey(String email) {
    return EMAIL_PREFIX + email;
  }
}
