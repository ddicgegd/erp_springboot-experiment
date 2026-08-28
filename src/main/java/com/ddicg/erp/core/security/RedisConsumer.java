package com.ddicg.erp.core.security;

import java.time.Duration;
import java.util.List;

import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Autowired
  private StringRedisTemplate redisTemplate;

  @Autowired
  @Lazy
  private StreamMessageListenerContainer<String, MapRecord<String, String, String>> redisContainer;

  @Autowired
  private CacheManager cacheManager;

  @Override
  public void onMessage(MapRecord<String, String, String> message) {
    try {
      log.info("Nhận tin nhắn xóa cache từ Stream: {}", message.getValue());
      String id = message.getValue().get("id");
      processMessageWithRetry(message, RedisTable.LOCK_CACHE_EVICT.key(id));
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
        redisTemplate.opsForStream().delete(RedisTable.STREAM_CACHE_EVICT.getPrefix(), msg.getId());
        log.info("Đã xóa lock key và stream message thành công.");
        return;
      }

      log.warn("Hệ thống đích chưa sẵn sàng (Lần thử {}/{}). Đợi 5 giây...", attempt, maxRetries);
      try {
        Thread.sleep(Duration.ofSeconds(5).toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("Thread bị gián đoạn trong khi chờ retry: {}", e.getMessage());
        return;
      }
    }

    log.error("Quá số lần retry tối đa ({}). Không thể xóa cache cho message: {}", maxRetries, msg.getValue());
  }

  private boolean checkTargetSystem() {
    return true;
  }

  private void evictCache(MapRecord<String, String, String> msg) {
    try {
      String id = msg.getValue().get("id");
      if (id != null) {
        log.info("Đang xóa cache L1 (Caffeine) cho sản phẩm ID: {}", id);

        Cache productCache = cacheManager.getCache("products");
        if (productCache != null) {
          productCache.evict(id);
          try {
            Long numericId = Long.parseLong(id);
            productCache.evict(numericId);
          } catch (NumberFormatException ignored) {}
          log.info("Đã xóa cache L1 thành công.");
        }
      }
    } catch (Exception e) {
      log.error("Lỗi khi xóa cache: {}", e.getMessage(), e);
    }
  }
}
