package com.ddicg.erp.core.exception;

import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisImmutabilityAspectTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private JoinPoint joinPoint;

    @InjectMocks
    private RedisImmutabilityAspect aspect;

    @Test
    void testAspectBlocksImmutableTableWithExistingKey() {
        when(joinPoint.getArgs()).thenReturn(new Object[]{RedisTable.AUTH_OTP_VERIFICATION, "user123", "999999"});
        when(redisTemplate.hasKey("auth:otp:verification:user123")).thenReturn(true);

        assertThatThrownBy(() -> aspect.validateWritePermission(joinPoint))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("bất biến (Immutable)");
    }

    @Test
    void testAspectAllowsImmutableTableWithNewKey() {
        when(joinPoint.getArgs()).thenReturn(new Object[]{RedisTable.AUTH_OTP_VERIFICATION, "user123", "999999"});
        when(redisTemplate.hasKey("auth:otp:verification:user123")).thenReturn(false);

        assertThatCode(() -> aspect.validateWritePermission(joinPoint))
                .doesNotThrowAnyException();
    }

    @Test
    void testAspectAllowsMutableTableEvenIfKeyExists() {
        when(joinPoint.getArgs()).thenReturn(new Object[]{RedisTable.CART_ITEMS, "user123", "SKU-A", 2});

        assertThatCode(() -> aspect.validateWritePermission(joinPoint))
                .doesNotThrowAnyException();

        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    void testAspectBlocksRawStringKeyWhenMatchesImmutableTable() {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"auth:action:recovery:token:tok-abc", "email@test.com"});
        when(redisTemplate.hasKey("auth:action:recovery:token:tok-abc")).thenReturn(true);

        assertThatThrownBy(() -> aspect.validateWritePermission(joinPoint))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("bất biến (Immutable)");
    }

    @Test
    void testAspectAllowsRawStringKeyWhenNotExists() {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"auth:action:recovery:token:tok-new", "email@test.com"});
        when(redisTemplate.hasKey("auth:action:recovery:token:tok-new")).thenReturn(false);

        assertThatCode(() -> aspect.validateWritePermission(joinPoint))
                .doesNotThrowAnyException();
    }
}
