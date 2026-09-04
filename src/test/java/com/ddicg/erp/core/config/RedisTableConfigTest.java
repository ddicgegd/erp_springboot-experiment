package com.ddicg.erp.core.config;

import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RedisTableConfigTest {

    @Test
    void testRedisTableEnumPrefixesAndImmutability() {
        assertThat(RedisTable.AUTH_SESSION_PROFILE.key("1")).isEqualTo("auth:session:profile:1");
        assertThat(RedisTable.AUTH_SESSION_DEVICE.key("1")).isEqualTo("auth:session:device:1");
        assertThat(RedisTable.AUTH_OTP_VERIFICATION.key("abc")).isEqualTo("auth:otp:verification:abc");
        assertThat(RedisTable.AUTH_RECOVERY_TOKEN.key("tok123")).isEqualTo("auth:action:recovery:token:tok123");
        assertThat(RedisTable.AUTH_RECOVERY_EMAIL.key("test@example.com")).isEqualTo("auth:action:recovery:email:test@example.com");
        assertThat(RedisTable.AUTH_GUARD_COOLDOWN.key("1")).isEqualTo("auth:guard:cooldown:1");
        assertThat(RedisTable.CART_ITEMS.key("1")).isEqualTo("cart:items:1");
        assertThat(RedisTable.CART_GUEST_ITEMS.key("guest123")).isEqualTo("cart:guest:items:guest123");
        assertThat(RedisTable.CATALOG_PRODUCT.key("100")).isEqualTo("catalog:product:detail:100");
        assertThat(RedisTable.CATALOG_REC.key("rec1")).isEqualTo("catalog:product:rec:rec1");
        assertThat(RedisTable.VOUCHER_INFO.key("SALE20")).isEqualTo("voucher:info:SALE20");
        assertThat(RedisTable.LOCK_ORDER.key("ORD-01")).isEqualTo("lock:order:process:ORD-01");
        assertThat(RedisTable.STREAM_CACHE_EVICT.getPrefix()).isEqualTo("stream:event:cache_evict");
        assertThat(RedisTable.STREAM_CACHE_EVICT.key(null)).isEqualTo("stream:event:cache_evict");

        assertThat(RedisTable.AUTH_OTP_VERIFICATION.isImmutable()).isTrue();
        assertThat(RedisTable.AUTH_RECOVERY_TOKEN.isImmutable()).isTrue();
        assertThat(RedisTable.AUTH_GUARD_COOLDOWN.isImmutable()).isTrue();
        assertThat(RedisTable.LOCK_INVENTORY.isImmutable()).isTrue();

        assertThat(RedisTable.CART_ITEMS.isImmutable()).isFalse();
        assertThat(RedisTable.CART_GUEST_ITEMS.isImmutable()).isFalse();
        assertThat(RedisTable.AUTH_SESSION_PROFILE.isImmutable()).isFalse();
        assertThat(RedisTable.CATALOG_PRODUCT.isImmutable()).isFalse();

        assertThat(RedisTable.fromKey("auth:otp:verification:xyz")).contains(RedisTable.AUTH_OTP_VERIFICATION);
        assertThat(RedisTable.fromKey("cart:items:user1")).contains(RedisTable.CART_ITEMS);
        assertThat(RedisTable.fromKey("cart:guest:items:guest123")).contains(RedisTable.CART_GUEST_ITEMS);
        assertThat(RedisTable.fromKey("unknown:key")).isEmpty();
        assertThat(RedisTable.fromKey(null)).isEmpty();
    }

    @Test
    void testRedisConfiguration() {
        RedisConfiguration config = new RedisConfiguration();
        RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, Object> template = config.redisTemplate(mockFactory);
        assertThat(template.getConnectionFactory()).isSameAs(mockFactory);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);

        var container = config.redisContainer(mockFactory);
        assertThat(container).isNotNull();
    }

    @Test
    void testRedisServiceWithTableEnum() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> mockTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> mockValueOps = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> mockHashOps = mock(HashOperations.class);

        when(mockTemplate.opsForValue()).thenReturn(mockValueOps);
        when(mockTemplate.opsForHash()).thenReturn(mockHashOps);

        RedisService redisService = new RedisService(mockTemplate);

        redisService.setValueWithExpiry(RedisTable.AUTH_OTP_VERIFICATION, "code123", "user@test.com", 5, TimeUnit.MINUTES);
        verify(mockValueOps).set("auth:otp:verification:code123", "user@test.com", 5, TimeUnit.MINUTES);

        when(mockValueOps.get("auth:otp:verification:code123")).thenReturn("user@test.com");
        Object val = redisService.getValue(RedisTable.AUTH_OTP_VERIFICATION, "code123");
        assertThat(val).isEqualTo("user@test.com");

        redisService.hSet(RedisTable.CART_ITEMS, "user1", "SKU-A", 2);
        verify(mockHashOps).put("cart:items:user1", "SKU-A", 2);

        when(mockHashOps.get("cart:items:user1", "SKU-A")).thenReturn(2);
        Object qty = redisService.hGet(RedisTable.CART_ITEMS, "user1", "SKU-A");
        assertThat(qty).isEqualTo(2);

        when(mockTemplate.hasKey("auth:guard:cooldown:user1")).thenReturn(true);
        boolean hasCooldown = redisService.hasKey(RedisTable.AUTH_GUARD_COOLDOWN, "user1");
        assertThat(hasCooldown).isTrue();
    }
}
