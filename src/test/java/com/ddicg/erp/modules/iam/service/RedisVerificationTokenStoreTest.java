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
class RedisVerificationTokenStoreTest {

    @Mock
    private RedisService redisService;

    @InjectMocks
    private RedisVerificationTokenStore redisVerificationTokenStore;

    private String rawToken;
    private String expectedHash;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        rawToken = "test-uuid-verification-token";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        expectedHash = HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("save token mới: Băm SHA-256 và lưu cặp key vào Redis")
    void save_WhenNewToken_ShouldHashAndStore() {
        String email = "test@example.com";
        Duration ttl = Duration.ofMinutes(15);

        when(redisService.getValue(RedisTable.AUTH_VERIFICATION_EMAIL, email)).thenReturn(null);

        redisVerificationTokenStore.save(email, rawToken, ttl);

        verify(redisService).setValueWithExpiry(
                eq(RedisTable.AUTH_VERIFICATION_TOKEN),
                eq(expectedHash),
                eq(email),
                eq(900L),
                eq(TimeUnit.SECONDS)
        );

        verify(redisService).setValueWithExpiry(
                eq(RedisTable.AUTH_VERIFICATION_EMAIL),
                eq(email),
                eq(expectedHash),
                eq(900L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("save token khi đã có token cũ (Token Rotation): Xóa token cũ trước khi lưu token mới")
    void save_WhenOldTokenExists_ShouldRotateAndRemoveOldTokenFirst() {
        String email = "test@example.com";
        String oldHash = "old_token_hash";
        Duration ttl = Duration.ofMinutes(15);

        when(redisService.getValue(RedisTable.AUTH_VERIFICATION_EMAIL, email)).thenReturn(oldHash);

        redisVerificationTokenStore.save(email, rawToken, ttl);

        verify(redisService).delete(RedisTable.AUTH_VERIFICATION_TOKEN, oldHash);
        verify(redisService).delete(RedisTable.AUTH_VERIFICATION_EMAIL, email);

        verify(redisService).setValueWithExpiry(
                eq(RedisTable.AUTH_VERIFICATION_TOKEN),
                eq(expectedHash),
                eq(email),
                eq(900L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("findEmailByToken: Băm raw token rồi tra cứu trong Redis")
    void findEmailByToken_ShouldHashAndLookup() {
        when(redisService.getValue(RedisTable.AUTH_VERIFICATION_TOKEN, expectedHash)).thenReturn("user@example.com");

        Optional<String> email = redisVerificationTokenStore.findEmailByToken(rawToken);

        assertTrue(email.isPresent());
        assertEquals("user@example.com", email.get());
    }

    @Test
    @DisplayName("consume: Băm raw token rồi xóa cả token hash và email khỏi Redis")
    void consume_ShouldHashAndDeleteBothKeys() {
        String email = "user@example.com";

        redisVerificationTokenStore.consume(email, rawToken);

        verify(redisService).delete(RedisTable.AUTH_VERIFICATION_TOKEN, expectedHash);
        verify(redisService).delete(RedisTable.AUTH_VERIFICATION_EMAIL, email);
    }
}
