package com.ddicg.erp.core.config;

import lombok.Getter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;
import java.util.Optional;

@Configuration
public class RedisConfiguration {

  @Getter
  public enum RedisTable {
    AUTH_OTP_VERIFICATION("auth:otp:verification:", true),
    AUTH_RECOVERY_TOKEN("auth:action:recovery:token:", true),
    AUTH_RECOVERY_EMAIL("auth:action:recovery:email:", true),
    AUTH_GUARD_COOLDOWN("auth:guard:cooldown:", true),
    LOCK_ORDER("lock:order:process:", true),
    LOCK_INVENTORY("lock:inventory:sku:", true),
    LOCK_VOUCHER("lock:voucher:apply:", true),
    LOCK_CACHE_EVICT("lock:cache_evict:", true),

    AUTH_SESSION_PROFILE("auth:session:profile:", false),
    AUTH_SESSION_DEVICE("auth:session:device:", false),
    CART_ITEMS("cart:items:", false),
    CART_GUEST_ITEMS("cart:guest:items:", false),
    CART_CHECKOUT_DRAFT("cart:checkout_draft:", false),
    CATALOG_PRODUCT("catalog:product:detail:", false),
    CATALOG_REC("catalog:product:rec:", false),
    CATALOG_CATEGORY("catalog:category:tree", false),
    VOUCHER_INFO("voucher:info:", false),
    VOUCHER_QUOTA("voucher:quota:", false),
    STREAM_CACHE_EVICT("stream:event:cache_evict", false),
    RATELIMIT("ratelimit:", false);

    private final String prefix;
    private final boolean immutable;

    RedisTable(String prefix, boolean immutable) {
      this.prefix = prefix;
      this.immutable = immutable;
    }

    public String key(Object id) {
      if (id == null) {
        return this.prefix;
      }
      return this.prefix + id;
    }

    public static Optional<RedisTable> fromKey(String key) {
      if (key == null || key.isBlank()) {
        return Optional.empty();
      }
      for (RedisTable table : values()) {
        if (key.startsWith(table.getPrefix())) {
          return Optional.of(table);
        }
      }
      return Optional.empty();
    }
  }

  @Bean
  @Primary
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());

    GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
    template.setValueSerializer(jsonSerializer);
    template.setHashValueSerializer(jsonSerializer);
    template.afterPropertiesSet();
    return template;
  }

  @Bean(name = "RedisContainer")
  public StreamMessageListenerContainer<String, MapRecord<String, String, String>> redisContainer(
      RedisConnectionFactory connectionFactory) {
    StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
        StreamMessageListenerContainerOptions.builder()
            .pollTimeout(Duration.ofSeconds(1))
            .build();
    return StreamMessageListenerContainer.create(connectionFactory, options);
  }
}
