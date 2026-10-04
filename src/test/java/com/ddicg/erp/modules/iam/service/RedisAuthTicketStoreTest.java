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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisAuthTicketStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisAuthTicketStore store;

    @BeforeEach
    void setUp() {
        store = new RedisAuthTicketStore(redisTemplate);
    }

    @Test
    void createTicket_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String ticket = store.createTicket(100L);

        assertNotNull(ticket);
        assertFalse(ticket.isEmpty());
        verify(valueOperations).set("auth_ticket:" + ticket, "100", Duration.ofMinutes(2));
    }

    @Test
    void createTicket_ThrowsException_WhenUserIdNull() {
        assertThrows(IllegalArgumentException.class, () -> store.createTicket(null));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void consumeTicket_Success() {
        String ticket = "some-random-ticket";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("auth_ticket:" + ticket)).thenReturn("200");

        Long userId = store.consumeTicket(ticket);

        assertEquals(200L, userId);
        verify(valueOperations).getAndDelete("auth_ticket:" + ticket);
    }

    @Test
    void consumeTicket_ThrowsInvalidOrExpired_WhenTicketNull() {
        BusinessException exception = assertThrows(BusinessException.class, () -> store.consumeTicket(null));
        assertEquals(ErrorCode.INVALID_OR_EXPIRED_TICKET, exception.getErrorCode());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void consumeTicket_ThrowsInvalidOrExpired_WhenRedisReturnsNull() {
        String ticket = "expired-ticket";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("auth_ticket:" + ticket)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> store.consumeTicket(ticket));
        assertEquals(ErrorCode.INVALID_OR_EXPIRED_TICKET, exception.getErrorCode());
    }
}
