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
class RedisRecoveryTokenStoreTest {

    @Mock
    private RedisService redisService;

    @InjectMocks
    private RedisRecoveryTokenStore redisRecoveryTokenStore;

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
    @DisplayName("save token mới: Băm SHA-256 và lưu cặp key vào Redis")
    void save_WhenNewToken_ShouldHashAndStore() {
        when(redisService.getValue(RedisTable.AUTH_RECOVERY_EMAIL, "user@example.com")).thenReturn(null);

        redisRecoveryTokenStore.save("user@example.com", rawToken, Duration.ofMinutes(20));

        verify(redisService).setValueWithExpiry(
                eq(RedisTable.AUTH_RECOVERY_TOKEN),
                eq(expectedHash),
                eq("user@example.com"),
                eq(1200L),
                eq(TimeUnit.SECONDS)
        );
        verify(redisService).setValueWithExpiry(
                eq(RedisTable.AUTH_RECOVERY_EMAIL),
                eq("user@example.com"),
                eq(expectedHash),
                eq(1200L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("save token khi đã có token cũ (Token Rotation): Xóa token cũ trước khi lưu token mới")
    void save_WhenOldTokenExists_ShouldRotateAndRemoveOldTokenFirst() {
        when(redisService.getValue(RedisTable.AUTH_RECOVERY_EMAIL, "user@example.com")).thenReturn("old-sha256-hash");

        redisRecoveryTokenStore.save("user@example.com", rawToken, Duration.ofMinutes(10));

        verify(redisService).delete(RedisTable.AUTH_RECOVERY_TOKEN, "old-sha256-hash");
        verify(redisService).delete(RedisTable.AUTH_RECOVERY_EMAIL, "user@example.com");

        verify(redisService).setValueWithExpiry(
                eq(RedisTable.AUTH_RECOVERY_TOKEN),
                eq(expectedHash),
                eq("user@example.com"),
                eq(600L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("findEmailByToken: Băm raw token rồi tra cứu trong Redis")
    void findEmailByToken_ShouldHashAndLookup() {
        when(redisService.getValue(RedisTable.AUTH_RECOVERY_TOKEN, expectedHash)).thenReturn("user@example.com");

        Optional<String> emailOpt = redisRecoveryTokenStore.findEmailByToken(rawToken);

        assertTrue(emailOpt.isPresent());
        assertEquals("user@example.com", emailOpt.get());
    }

    @Test
    @DisplayName("consume: Băm raw token rồi xóa cả token hash và email khỏi Redis")
    void consume_ShouldHashAndDeleteBothKeys() {
        redisRecoveryTokenStore.consume("user@example.com", rawToken);

        verify(redisService).delete(RedisTable.AUTH_RECOVERY_TOKEN, expectedHash);
        verify(redisService).delete(RedisTable.AUTH_RECOVERY_EMAIL, "user@example.com");
    }
}
