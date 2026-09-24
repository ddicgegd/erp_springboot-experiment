package com.ddicg.erp.core.config;

import com.ddicg.erp.core.config.cache.RedisTemplateProvider;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RedisConfiguration {

  @Getter
  public enum RedisTable {
    AUTH_OTP_VERIFICATION("auth:otp:verification:", true, 0),
    AUTH_RECOVERY_TOKEN("auth:action:recovery:token:", true, 0),
    AUTH_RECOVERY_EMAIL("auth:action:recovery:email:", true, 0),
    AUTH_GUARD_COOLDOWN("auth:guard:cooldown:", true, 0),
    LOCK_ORDER("lock:order:process:", true, 0),
    LOCK_INVENTORY("lock:inventory:sku:", true, 0),
    LOCK_VOUCHER("lock:voucher:apply:", true, 0),
    LOCK_CACHE_EVICT("lock:cache_evict:", true, 0),

    AUTH_SESSION_PROFILE("auth:session:profile:", false, 0),
    AUTH_SESSION_DEVICE("auth:session:device:", false, 0),
    CART_ITEMS("cart:items:", false, 0),
    CART_GUEST_ITEMS("cart:guest:items:", false, 0),
    CART_CHECKOUT_DRAFT("cart:checkout_draft:", false, 0),
    CATALOG_PRODUCT("catalog:product:detail:", false, 0),
    CATALOG_REC("catalog:product:rec:", false, 0),
    CATALOG_CATEGORY("catalog:category:tree", false, 0),
    VOUCHER_INFO("voucher:info:", false, 0),
    VOUCHER_QUOTA("voucher:quota:", false, 0),
    STREAM_CACHE_EVICT("stream:event:cache_evict", false, 0),
    RATELIMIT("ratelimit:", false, 0),

    BOOKMARK_SAVED("bookmark:saved:", false, 0),
    BOOKMARK_STAGING("bookmark:staging:", false, 0),

    NOTIFICATION_DEDUP("notification:dedup:", true, 0),
    NOTIFICATION_RATELIMIT("notification:ratelimit:", false, 0),

    AUTH_RECOVERY_COOLDOWN("auth:action:recovery:cooldown:", false, 0),
    AUTH_RECOVERY_QUOTA("auth:action:recovery:quota:", false, 0),

    AUTH_VERIFICATION_TOKEN("auth:action:verification:token:", true, 0),
    AUTH_VERIFICATION_EMAIL("auth:action:verification:email:", true, 0),
    AUTH_VERIFICATION_COOLDOWN("auth:action:verification:cooldown:", false, 0),
    AUTH_VERIFICATION_QUOTA("auth:action:verification:quota:", false, 0);

    private final String prefix;
    private final boolean immutable;
    private final int database;

    RedisTable(String prefix, boolean immutable) {
      this(prefix, immutable, 0);
    }

    RedisTable(String prefix, boolean immutable, int database) {
      this.prefix = prefix;
      this.immutable = immutable;
      this.database = database;
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
    return createConfiguredTemplate(connectionFactory);
  }

  @Bean
  public RedisTemplateProvider redisTemplateProvider(
      @Value("${spring.data.redis.host:localhost}") String host,
      @Value("${spring.data.redis.port:6379}") int port,
      @Value("${spring.data.redis.username:}") String username,
      @Value("${spring.data.redis.password:}") String password,
      RedisConnectionFactory primaryConnectionFactory,
      RedisTemplate<String, Object> primaryTemplate) {

    Map<Integer, RedisTemplate<String, Object>> cache = new ConcurrentHashMap<>();
    cache.put(0, primaryTemplate);

    return dbIndex -> cache.computeIfAbsent(dbIndex, db -> {
      if (db == 0) {
        return primaryTemplate;
      }
      try {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(db);
        if (username != null && !username.isBlank()) {
          config.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
          config.setPassword(RedisPassword.of(password));
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return createConfiguredTemplate(factory);
      } catch (Exception e) {
        return primaryTemplate;
      }
    });
  }

  public static RedisTemplate<String, Object> createConfiguredTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);
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
