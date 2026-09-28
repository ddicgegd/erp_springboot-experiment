package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCredentialTokenStore implements CredentialTokenStore {

    private final RedisService redisService;

    @Override
    public void issueToken(Long userId, String email, String rawToken, Duration ttl) {
        long seconds = ttl.toSeconds();
        String tokenHash = hashToken(rawToken);
        String payload = (userId != null ? userId : "") + ":" + email;

        redisService.setValueWithExpiry(RedisTable.AUTH_CREDENTIAL_TOKEN, tokenHash, payload, seconds, TimeUnit.SECONDS);
        if (userId != null) {
            redisService.setValueWithExpiry(RedisTable.AUTH_CREDENTIAL_LOCK, userId, tokenHash, seconds, TimeUnit.SECONDS);
        }
        log.info("Phát hành token đổi credentials cho userId: {}, email: {} với hạn: {}s", userId, email, seconds);
    }

    @Override
    public boolean isRequestLocked(Long userId) {
        if (userId == null) {
            return false;
        }
        return redisService.hasKey(RedisTable.AUTH_CREDENTIAL_LOCK, userId);
    }

    @Override
    public Optional<TokenInfo> activateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = hashToken(rawToken);
        String payload = (String) redisService.getValue(RedisTable.AUTH_CREDENTIAL_TOKEN, tokenHash);
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        Long remainingSeconds = redisService.getExpireSeconds(RedisTable.AUTH_CREDENTIAL_TOKEN.key(tokenHash));
        long activeSeconds = (remainingSeconds != null && remainingSeconds > 0) ? remainingSeconds : 300L;

        Long userId = null;
        String email = payload;

        if (payload.contains(":")) {
            String[] parts = payload.split(":", 2);
            if (!parts[0].isBlank()) {
                try {
                    userId = Long.parseLong(parts[0]);
                } catch (NumberFormatException ignored) {}
            }
            email = parts[1];
        }

        if (userId != null) {
            redisService.setValueWithExpiry(RedisTable.AUTH_CREDENTIAL_ACTIVE, userId, "ACTIVE", activeSeconds, TimeUnit.SECONDS);
            log.info("Đã kích hoạt quyền ACTIVE đổi credentials cho userId: {} trong {}s", userId, activeSeconds);
        }

        // Xóa token pending sau khi đã active
        redisService.delete(RedisTable.AUTH_CREDENTIAL_TOKEN, tokenHash);

        return Optional.of(new TokenInfo(userId, email, activeSeconds));
    }

    @Override
    public boolean isUserActive(Long userId) {
        if (userId == null) {
            return false;
        }
        return redisService.hasKey(RedisTable.AUTH_CREDENTIAL_ACTIVE, userId);
    }

    @Override
    public Long getActiveRemainingSeconds(Long userId) {
        if (userId == null) {
            return null;
        }
        return redisService.getExpireSeconds(RedisTable.AUTH_CREDENTIAL_ACTIVE.key(userId));
    }

    @Override
    public void consumeActiveGrant(Long userId) {
        if (userId != null) {
            redisService.delete(RedisTable.AUTH_CREDENTIAL_ACTIVE, userId);
            redisService.delete(RedisTable.AUTH_CREDENTIAL_LOCK, userId);
            log.info("Đã thu hồi quyền ACTIVE đổi credentials cho userId: {}", userId);
        }
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
