package com.ddicg.erp.core.common.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Map;

@Service
public class RedisProducerService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    @Qualifier("RedisContainer")
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> redisContainer;

    public void sendEvictMessage(String id) {
        String lockKey = "lock:" + id;

        // 1. Kiểm tra trùng lặp trên lock key trước khi gửi
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(lockKey, "PENDING", Duration.ofMinutes(10));
        if (Boolean.FALSE.equals(isNew)) {
            return; // Đã có yêu cầu đang xử lý/chờ xử lý, bỏ qua để tránh trùng lặp
        }

        // 2. Gửi message chứa id sản phẩm vào hòm thư "redis-stream"
        redisTemplate.opsForStream().add(MapRecord.create("redis-stream", Map.of("id", id)));

        // 3. Kích hoạt bật Container nếu đang dừng
        if (!redisContainer.isRunning()) {
            redisContainer.start();
        }
    }
}
