package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisCredentialTokenStoreTest {

    @Mock
    private RedisService redisService;

    @InjectMocks
    private RedisCredentialTokenStore redisCredentialTokenStore;

    private String rawToken;
    private String expectedHash;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        rawToken = "test-raw-uuid-token-12345";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
        expectedHash = HexFormat.of().formatHex(hash);
    }

    @Test
    @DisplayName("issueToken: Băm SHA-256 và lưu mapping AUTH_CREDENTIAL_TOKEN và AUTH_CREDENTIAL_LOCK")
    void issueToken_ShouldHashAndStore() {
        String email = "test@example.com";
        Long userId = 100L;
        Duration ttl = Duration.ofMinutes(5);

        redisCredentialTokenStore.issueToken(userId, email, rawToken, ttl);

        verify(redisService).setValueWithExpiry(eq(RedisTable.AUTH_CREDENTIAL_TOKEN), eq(expectedHash), eq("100:test@example.com"), eq(300L), eq(TimeUnit.SECONDS));
        verify(redisService).setValueWithExpiry(eq(RedisTable.AUTH_CREDENTIAL_LOCK), eq(userId), eq(expectedHash), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("isRequestLocked: Kiểm tra key trên Redis AUTH_CREDENTIAL_LOCK")
    void isRequestLocked_ShouldCheckRedis() {
        when(redisService.hasKey(RedisTable.AUTH_CREDENTIAL_LOCK, 100L)).thenReturn(true);

        boolean locked = redisCredentialTokenStore.isRequestLocked(100L);

        assertTrue(locked);
        verify(redisService).hasKey(RedisTable.AUTH_CREDENTIAL_LOCK, 100L);
    }

    @Test
    @DisplayName("activateToken: Lưu trạng thái ACTIVE với TTL còn lại vào AUTH_CREDENTIAL_ACTIVE và xóa token pending")
    void activateToken_ShouldStoreActiveSession() {
        when(redisService.getValue(RedisTable.AUTH_CREDENTIAL_TOKEN, expectedHash)).thenReturn("100:test@example.com");
        when(redisService.getExpireSeconds(RedisTable.AUTH_CREDENTIAL_TOKEN.key(expectedHash))).thenReturn(280L);

        Optional<CredentialTokenStore.TokenInfo> result = redisCredentialTokenStore.activateToken(rawToken);

        assertTrue(result.isPresent());
        assertEquals(100L, result.get().userId());
        assertEquals("test@example.com", result.get().email());
        assertEquals(280L, result.get().remainingSeconds());

        verify(redisService).setValueWithExpiry(eq(RedisTable.AUTH_CREDENTIAL_ACTIVE), eq(100L), eq("ACTIVE"), eq(280L), eq(TimeUnit.SECONDS));
        verify(redisService).delete(RedisTable.AUTH_CREDENTIAL_TOKEN, expectedHash);
    }

    @Test
    @DisplayName("isUserActive: Tra cứu In-Memory từ Redis AUTH_CREDENTIAL_ACTIVE")
    void isUserActive_WhenActive_ShouldReturnTrue() {
        when(redisService.hasKey(RedisTable.AUTH_CREDENTIAL_ACTIVE, 100L)).thenReturn(true);

        boolean active = redisCredentialTokenStore.isUserActive(100L);

        assertTrue(active);
    }

    @Test
    @DisplayName("consumeActiveGrant: Xóa key active và lock khỏi Redis")
    void consumeActiveGrant_ShouldDeleteKeys() {
        redisCredentialTokenStore.consumeActiveGrant(100L);

        verify(redisService).delete(RedisTable.AUTH_CREDENTIAL_ACTIVE, 100L);
        verify(redisService).delete(RedisTable.AUTH_CREDENTIAL_LOCK, 100L);
    }
}
