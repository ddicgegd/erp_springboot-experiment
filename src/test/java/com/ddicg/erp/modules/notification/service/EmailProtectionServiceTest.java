package com.ddicg.erp.modules.notification.service;

import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailProtectionServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private EmailProtectionServiceImpl emailProtectionService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("acquireDeduplicationLock - trả về true khi khóa mới được thiết lập thành công")
    void testAcquireDeduplicationLock_Success() {
        when(valueOperations.setIfAbsent(eq(RedisTable.NOTIFICATION_DEDUP.key("test-key")), eq("LOCKED"), eq(10L), eq(TimeUnit.MINUTES)))
                .thenReturn(true);

        boolean result = emailProtectionService.acquireDeduplicationLock("test-key", 10);
        assertTrue(result);
    }

    @Test
    @DisplayName("acquireDeduplicationLock - trả về false khi khóa đã tồn tại (chống gửi trùng lặp)")
    void testAcquireDeduplicationLock_Duplicate() {
        when(valueOperations.setIfAbsent(eq(RedisTable.NOTIFICATION_DEDUP.key("test-key")), eq("LOCKED"), eq(10L), eq(TimeUnit.MINUTES)))
                .thenReturn(false);

        boolean result = emailProtectionService.acquireDeduplicationLock("test-key", 10);
        assertFalse(result);
    }

    @Test
    @DisplayName("acquireDeduplicationLock - bỏ qua khi dedupKey là null hoặc rỗng")
    void testAcquireDeduplicationLock_EmptyKey() {
        assertTrue(emailProtectionService.acquireDeduplicationLock(null, 10));
        assertTrue(emailProtectionService.acquireDeduplicationLock("   ", 10));
        verifyNoInteractions(valueOperations);
    }

    @Test
    @DisplayName("acquireDeduplicationLock - fail-open an toàn khi Redis lỗi kết nối")
    void testAcquireDeduplicationLock_FailOpenOnException() {
        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any()))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        boolean result = emailProtectionService.acquireDeduplicationLock("error-key", 10);
        assertTrue(result, "Cơ chế Fail-Open phải trả về true khi Redis gặp sự cố");
    }

    @Test
    @DisplayName("allowDeliveryRate - trả về true khi số lượng gửi trong ngưỡng cho phép")
    void testAllowDeliveryRate_WithinLimit() {
        when(valueOperations.increment(eq(RedisTable.NOTIFICATION_RATELIMIT.key("user@example.com"))))
                .thenReturn(1L);

        boolean result = emailProtectionService.allowDeliveryRate("user@example.com", 5);
        assertTrue(result);
        verify(redisTemplate).expire(eq(RedisTable.NOTIFICATION_RATELIMIT.key("user@example.com")), eq(Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("allowDeliveryRate - trả về false khi số lượng gửi vượt ngưỡng (rate limited)")
    void testAllowDeliveryRate_Exceeded() {
        when(valueOperations.increment(eq(RedisTable.NOTIFICATION_RATELIMIT.key("user@example.com"))))
                .thenReturn(6L);

        boolean result = emailProtectionService.allowDeliveryRate("user@example.com", 5);
        assertFalse(result);
    }

    @Test
    @DisplayName("allowDeliveryRate - fail-open an toàn khi Redis lỗi kết nối")
    void testAllowDeliveryRate_FailOpenOnException() {
        when(valueOperations.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis timeout"));

        boolean result = emailProtectionService.allowDeliveryRate("user@example.com", 5);
        assertTrue(result, "Cơ chế Fail-Open phải trả về true khi Redis gặp sự cố");
    }
}
