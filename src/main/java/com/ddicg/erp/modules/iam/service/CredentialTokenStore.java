package com.ddicg.erp.modules.iam.service;

import java.time.Duration;
import java.util.Optional;

public interface CredentialTokenStore {

    record TokenInfo(Long userId, String email, Long remainingSeconds) {}

    void issueToken(Long userId, String email, String rawToken, Duration ttl);

    boolean isRequestLocked(Long userId);

    Optional<TokenInfo> activateToken(String rawToken);

    boolean isUserActive(Long userId);

    Long getActiveRemainingSeconds(Long userId);

    void consumeActiveGrant(Long userId);
}
