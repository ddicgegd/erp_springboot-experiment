package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisOAuth2StateStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisOAuth2StateStore store;

    @BeforeEach
    void setUp() {
        store = new RedisOAuth2StateStore(redisTemplate);
    }

    @Test
    void saveState_Success() {
        String state = "random_secure_state_123";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        store.saveState(state);

        verify(redisTemplate).opsForValue();
        verify(valueOperations).set("oauth_state:" + state, "true", Duration.ofMinutes(10));
    }

    @Test
    void saveState_ThrowsException_WhenStateIsNull() {
        assertThrows(IllegalArgumentException.class, () -> store.saveState(null));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void saveState_ThrowsException_WhenStateIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> store.saveState("   "));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void validateAndConsume_Success() {
        String state = "valid_state";
        when(redisTemplate.delete("oauth_state:" + state)).thenReturn(true);

        boolean result = store.validateAndConsume(state, state);
        
        assertTrue(result);
        verify(redisTemplate).delete("oauth_state:" + state);
    }

    @Test
    void validateAndConsume_ThrowsMismatch_WhenCookieStateIsNull() {
        BusinessException exception = assertThrows(BusinessException.class, 
            () -> store.validateAndConsume(null, "state"));
        assertEquals(ErrorCode.INVALID_OAUTH_STATE_MISMATCH, exception.getErrorCode());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void validateAndConsume_ThrowsMismatch_WhenQueryStateIsNull() {
        BusinessException exception = assertThrows(BusinessException.class, 
            () -> store.validateAndConsume("state", null));
        assertEquals(ErrorCode.INVALID_OAUTH_STATE_MISMATCH, exception.getErrorCode());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void validateAndConsume_ThrowsMismatch_WhenStatesDoNotMatch() {
        BusinessException exception = assertThrows(BusinessException.class, 
            () -> store.validateAndConsume("state1", "state2"));
        assertEquals(ErrorCode.INVALID_OAUTH_STATE_MISMATCH, exception.getErrorCode());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void validateAndConsume_ThrowsExpiredOrReplayed_WhenRedisReturnsFalse() {
        String state = "replayed_state";
        when(redisTemplate.delete("oauth_state:" + state)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, 
            () -> store.validateAndConsume(state, state));
        assertEquals(ErrorCode.EXPIRED_OR_REPLAYED_STATE, exception.getErrorCode());
        verify(redisTemplate).delete("oauth_state:" + state);
    }
}
