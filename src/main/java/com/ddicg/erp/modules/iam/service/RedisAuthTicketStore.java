package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RedisAuthTicketStore implements AuthTicketStore {

    private static final String PREFIX = "auth_ticket:";
    private static final Duration TTL = Duration.ofMinutes(2); // 120s
    
    private final StringRedisTemplate redisTemplate;

    public RedisAuthTicketStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String createTicket(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        String ticket = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(PREFIX + ticket, userId.toString(), TTL);
        return ticket;
    }

    @Override
    public Long consumeTicket(String ticket) {
        if (ticket == null || ticket.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_OR_EXPIRED_TICKET);
        }
        
        String key = PREFIX + ticket;
        // Atomic Get-and-Delete (Requires Redis 6.2+)
        String userIdStr = redisTemplate.opsForValue().getAndDelete(key);
        
        if (userIdStr == null) {
            throw new BusinessException(ErrorCode.INVALID_OR_EXPIRED_TICKET);
        }
        
        return Long.parseLong(userIdStr);
    }
}
