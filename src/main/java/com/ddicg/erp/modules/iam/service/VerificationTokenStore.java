package com.ddicg.erp.modules.iam.service;

import java.time.Duration;
import java.util.Optional;

public interface VerificationTokenStore {

  Optional<String> findTokenByEmail(String email);

  Optional<String> findEmailByToken(String token);

  void save(String email, String token, Duration ttl);

  void consume(String email, String token);
}
