package com.ddicg.erp.core.security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.time.Duration;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RedisConsumer implements StreamListener<String, MapRecord<String, String, String>> {
    private static final Logger log = LoggerFactory.getLogger(RedisConsumer.class);


  @Autowired
  private StringRedisTemplate redisTemplate;

  @Autowired
  @Lazy
  @Qualifier("RedisContainer")
  private StreamMessageListenerContainer<String, MapRecord<String, String, String>> redisContainer;

  @Autowired
  private CacheManager cacheManager;

  @Override
  public void onMessage(MapRecord<String, String, String> message) {
    try {
      log.info("Nhận tin nhắn xóa cache từ Stream: {}", message.getValue());
      processMessageWithRetry(message, "lock:" + message.getValue().get("id"));
    } finally {
      log.info("Tạm dừng Container để chờ hệ thống ổn định hoặc xử lý xong.");
      redisContainer.stop();
    }
  }

  private void processMessageWithRetry(MapRecord<String, String, String> msg, String lockKey) {
    int maxRetries = 30; // ~2.5 phút (30 * 5 giây)
    int attempt = 0;

    while (attempt < maxRetries) {
      attempt++;
      if (checkTargetSystem()) {
        log.info("Hệ thống sẵn sàng. Thực hiện xóa cache...");
        evictCache(msg);

        // Xóa Key Lock và Message trong Stream
        redisTemplate.delete(List.of(lockKey));
        redisTemplate.opsForStream().delete("redis-stream", msg.getId());
        log.info("Đã xóa lock key và stream message thành công.");
        return;
      }

      // Hệ thống quá tải: Dừng container ngay lập tức để không pull thêm tin nhắn mới
      if (redisContainer.isRunning()) {
        redisContainer.stop();
        log.info("Đã dừng Container nhận tin nhắn do hệ thống chưa sẵn sàng.");
      }

      // Gia hạn lock 10 phút, ngủ 5s rồi lặp lại kiểm tra
      log.warn("Hệ thống chưa sẵn sàng (lần {}/{}}). Gia hạn lock và thử lại sau 5 giây...", attempt, maxRetries);
      redisTemplate.expire(lockKey, Duration.ofMinutes(10));
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }

    // Hết số lần thử - xóa lock để cho phép message mới xử lý
    log.error("Hệ thống không sẵn sàng sau {} lần thử. Bỏ qua message {}.", maxRetries, msg.getId());
    redisTemplate.delete(List.of(lockKey));
  }

  private void evictCache(MapRecord<String, String, String> msg) {
    try {
      String idStr = msg.getValue().get("id");
      if (idStr != null) {
        Long productId = Long.valueOf(idStr);
        
        // 1. Xóa cache chi tiết sản phẩm
        Cache productDetailsCache = cacheManager.getCache("productDetails");
        if (productDetailsCache != null) {
          productDetailsCache.evict(productId);
          log.info("Đã xóa sản phẩm ID {} khỏi cache 'productDetails'.", productId);
        }
        
        // 2. Clear cache danh sách sản phẩm
        Cache productsCache = cacheManager.getCache("products");
        if (productsCache != null) {
          productsCache.clear();
          log.info("Đã clear cache danh sách 'products'.");
        }

        // 3. Xóa cache thuộc tính sản phẩm (key là String productId)
        Cache attributesCache = cacheManager.getCache("attributes");
        if (attributesCache != null) {
          attributesCache.evict(idStr);
          log.info("Đã xóa thuộc tính sản phẩm ID {} khỏi cache 'attributes'.", idStr);
        }
      }
    } catch (Exception e) {
      log.error("Lỗi khi thực hiện xóa cache: {}", e.getMessage(), e);
    }
  }

  private boolean checkTargetSystem() {
    return true; // Logic check hệ thống
  }
}