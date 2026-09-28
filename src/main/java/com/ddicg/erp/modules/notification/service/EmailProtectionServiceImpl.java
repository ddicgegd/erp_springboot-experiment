package com.ddicg.erp.modules.notification.service;

import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailProtectionServiceImpl implements EmailProtectionService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean acquireDeduplicationLock(String dedupKey, long ttlMinutes) {
        if (dedupKey == null || dedupKey.trim().isEmpty()) {
            return true;
        }

        String fullKey = RedisTable.NOTIFICATION_DEDUP.key(dedupKey.trim());
        try {
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(fullKey, "LOCKED", ttlMinutes, TimeUnit.MINUTES);
            boolean acquired = Boolean.TRUE.equals(isNew);
            if (!acquired) {
                log.warn("[EmailProtection] Bỏ qua gửi email do trùng lặp deduplication key: {}", dedupKey);
            }
            return acquired;
        } catch (Exception e) {
            log.error("[EmailProtection] Lỗi kết nối Redis khi check deduplication key {}. Fail-open để không chặn email.", dedupKey, e);
            return true;
        }
    }

    @Override
    public void releaseDeduplicationLock(String dedupKey) {
        if (dedupKey == null || dedupKey.trim().isEmpty()) {
            return;
        }

        String fullKey = RedisTable.NOTIFICATION_DEDUP.key(dedupKey.trim());
        try {
            redisTemplate.delete(fullKey);
            log.info("[EmailProtection] Đã giải phóng deduplication lock cho key: {}", dedupKey);
        } catch (Exception e) {
            log.error("[EmailProtection] Lỗi khi giải phóng deduplication lock {}: {}", dedupKey, e.getMessage(), e);
        }
    }

    @Override
    public boolean allowDeliveryRate(String recipient, int maxPerMinute) {
        if (recipient == null || recipient.trim().isEmpty() || maxPerMinute <= 0) {
            return true;
        }

        String normalizedEmail = recipient.trim().toLowerCase();
        String fullKey = RedisTable.NOTIFICATION_RATELIMIT.key(normalizedEmail);

        try {
            Long count = redisTemplate.opsForValue().increment(fullKey);
            if (count != null && count == 1) {
                redisTemplate.expire(fullKey, Duration.ofMinutes(1));
            } else {
                Long ttl = redisTemplate.getExpire(fullKey, TimeUnit.SECONDS);
                if (ttl != null && ttl < 0) {
                    redisTemplate.expire(fullKey, Duration.ofMinutes(1));
                }
            }

            if (count != null && count > maxPerMinute) {
                log.warn("[EmailProtection] Email {} vượt ngưỡng rate limit ({} > {}/phút)", normalizedEmail, count, maxPerMinute);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("[EmailProtection] Lỗi kết nối Redis khi check rate limit cho {}. Fail-open để không chặn email.", normalizedEmail, e);
            return true;
        }
    }
}
