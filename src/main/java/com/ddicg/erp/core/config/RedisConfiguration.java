package com.ddicg.erp.core.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;

@Configuration
public class RedisConfiguration {

  /**
   * ObjectMapper dùng cho Redis: hỗ trợ migration class name từ package cũ sang mới.
   * GenericJackson2JsonRedisSerializer lưu full class name vào Redis —
   * khi đổi package, data cũ sẽ fail nếu không có mapping này.
   */
  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
    ObjectMapper objectMapper = new ObjectMapper();

    // Kích hoạt typing để Jackson biết deserialize đúng kiểu
    objectMapper.activateDefaultTyping(
        LaissezFaireSubTypeValidator.instance,
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY
    );

    // Không crash khi gặp property không biết
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Remap class name cũ → mới khi deserialize từ Redis
    objectMapper.addMixIn(Object.class, Object.class); // placeholder để trigger type resolution
    objectMapper.setTypeFactory(
        objectMapper.getTypeFactory().withClassLoader(
            new PackageMigrationClassLoader(
                Thread.currentThread().getContextClassLoader(),
                "com.anno.ERP_SpringBoot_Experiment.",
                "com.ddicg.erp."
            )
        )
    );

    GenericJackson2JsonRedisSerializer jsonRedisSerializer =
        new GenericJackson2JsonRedisSerializer(objectMapper);

    RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
    redisTemplate.setConnectionFactory(redisConnectionFactory);
    redisTemplate.setKeySerializer(new StringRedisSerializer());
    redisTemplate.setHashKeySerializer(new StringRedisSerializer());
    redisTemplate.setValueSerializer(jsonRedisSerializer);
    redisTemplate.setHashValueSerializer(jsonRedisSerializer);
    redisTemplate.afterPropertiesSet();
    return redisTemplate;
  }

  @Bean(name = "RedisContainer")
  public StreamMessageListenerContainer<String, MapRecord<String, String, String>> redisContainer(
      RedisConnectionFactory redisConnectionFactory) {
    StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
        StreamMessageListenerContainerOptions.builder()
            .pollTimeout(Duration.ofSeconds(1))
            .build();
    return StreamMessageListenerContainer.create(redisConnectionFactory, options);
  }
}
