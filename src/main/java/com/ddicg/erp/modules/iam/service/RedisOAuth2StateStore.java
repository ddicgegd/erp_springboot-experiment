package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisOAuth2StateStore implements OAuth2StateStore {

    private static final String PREFIX = "oauth_state:";
    private static final Duration TTL = Duration.ofMinutes(10);
    
    private final StringRedisTemplate redisTemplate;

    public RedisOAuth2StateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveState(String state) {
        if (state == null || state.trim().isEmpty()) {
            throw new IllegalArgumentException("State cannot be null or empty");
        }
        redisTemplate.opsForValue().set(PREFIX + state, "true", TTL);
    }

    @Override
    public boolean validateAndConsume(String cookieState, String queryState) {
        if (cookieState == null || cookieState.trim().isEmpty() || 
            queryState == null || queryState.trim().isEmpty() || 
            !queryState.equals(cookieState)) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH_STATE_MISMATCH);
        }

        String key = PREFIX + queryState;
        
        // delete() in Spring Data Redis executes the DEL command which is atomic
        Boolean deleted = redisTemplate.delete(key);
        
        if (Boolean.TRUE.equals(deleted)) {
            return true;
        } else {
            throw new BusinessException(ErrorCode.EXPIRED_OR_REPLAYED_STATE);
        }
    }
}
