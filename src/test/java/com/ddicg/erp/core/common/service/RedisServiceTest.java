package com.ddicg.erp.core.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @InjectMocks
    private RedisService redisService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    void testUnlink() {
        redisService.unlink("key1", "key2");

        verify(redisTemplate).unlink(List.of("key1", "key2"));
    }

    @Test
    void testSetValueWithJitter() {
        redisService.setValueWithJitter("cache:test", "val", 100, 30, TimeUnit.SECONDS);

        verify(valueOperations).set(eq("cache:test"), eq("val"), longThat(ttl -> ttl >= 100 && ttl <= 130), eq(TimeUnit.SECONDS));
    }

    @Test
    void testNullSentinel() {
        redisService.setNullSentinel("cache:empty", 60);
        verify(valueOperations).set("cache:empty", RedisService.NULL_SENTINEL, 60, TimeUnit.SECONDS);

        when(valueOperations.get("cache:empty")).thenReturn(RedisService.NULL_SENTINEL);
        assertThat(redisService.isNullSentinel("cache:empty")).isTrue();

        when(valueOperations.get("cache:valid")).thenReturn("actualData");
        assertThat(redisService.isNullSentinel("cache:valid")).isFalse();
    }

    @Test
    void testZSetOperations() {
        redisService.zAdd("leaderboard", "user:1", 100.0);
        verify(zSetOperations).add("leaderboard", "user:1", 100.0);

        when(zSetOperations.score("leaderboard", "user:1")).thenReturn(100.0);
        assertThat(redisService.zScore("leaderboard", "user:1")).isEqualTo(100.0);

        when(zSetOperations.reverseRank("leaderboard", "user:1")).thenReturn(0L);
        assertThat(redisService.zRevRank("leaderboard", "user:1")).isEqualTo(0L);
    }
}
