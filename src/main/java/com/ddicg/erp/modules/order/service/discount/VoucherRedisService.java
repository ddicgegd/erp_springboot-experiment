package com.ddicg.erp.modules.order.service.discount;

import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.modules.order.dto.VoucherCacheDto;
import com.ddicg.erp.modules.order.model.Voucher;
import com.ddicg.erp.modules.order.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherRedisService {

    private final VoucherRepository voucherRepository;
    private final RedisService redisService;
    private final RedissonClient redissonClient;

    /**
     * Nạp Voucher lên Redis với TTL = (endDate - now) kèm Jitter chống Cache Avalanche
     */
    public void cacheVoucher(Voucher voucher) {
        if (voucher == null || voucher.getCode() == null || voucher.getEndDate() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        long ttlSeconds = Duration.between(now, voucher.getEndDate()).getSeconds();

        if (ttlSeconds > 0 && voucher.isCurrentlyValid()) {
            String upperCode = voucher.getCode().trim().toUpperCase();
            VoucherCacheDto dto = VoucherCacheDto.fromEntity(voucher);
            // Áp dụng TTL Jitter ngẫu nhiên 30-180 giây
            redisService.setValueWithJitter(RedisTable.VOUCHER_INFO, upperCode, dto, ttlSeconds, 180, TimeUnit.SECONDS);
            log.info("⚡ VOUCHER_CACHED_ON_REDIS: Table: VOUCHER_INFO, Key: {} (Base TTL: {}s + Jitter)", upperCode, ttlSeconds);
        }
    }

    /**
     * Lấy Voucher hợp lệ từ Redis (Cache-aside + Null Caching + Mutex Lock chống Stampede)
     */
    public Optional<VoucherCacheDto> getValidVoucher(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        String upperCode = code.trim().toUpperCase();

        // 1. Kiểm tra Cache L2 (Redis)
        if (redisService.isNullSentinel(RedisTable.VOUCHER_INFO, upperCode)) {
            log.debug("🛡️ VOUCHER_NULL_CACHE_HIT: Table: VOUCHER_INFO, Key: {}", upperCode);
            return Optional.empty();
        }

        Object cached = redisService.getValue(RedisTable.VOUCHER_INFO, upperCode);
        if (cached instanceof VoucherCacheDto dto) {
            return dto.isCurrentlyValid() ? Optional.of(dto) : Optional.empty();
        }

        // 2. Cache Miss -> Giành Khóa Phân Tán (Mutex Lock) để bảo vệ Oracle DB
        String lockKey = RedisTable.LOCK_VOUCHER.key(upperCode);
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(2, 5, TimeUnit.SECONDS)) {
                try {
                    // Double-check cache sau khi nhận được lock
                    if (redisService.isNullSentinel(RedisTable.VOUCHER_INFO, upperCode)) {
                        return Optional.empty();
                    }
                    Object reCheck = redisService.getValue(RedisTable.VOUCHER_INFO, upperCode);
                    if (reCheck instanceof VoucherCacheDto dto) {
                        return dto.isCurrentlyValid() ? Optional.of(dto) : Optional.empty();
                    }

                    // Query Database gốc
                    Optional<Voucher> dbVoucherOpt = voucherRepository.findByCode(upperCode);
                    if (dbVoucherOpt.isPresent()) {
                        Voucher dbVoucher = dbVoucherOpt.get();
                        if (dbVoucher.isCurrentlyValid()) {
                            cacheVoucher(dbVoucher);
                            return Optional.of(VoucherCacheDto.fromEntity(dbVoucher));
                        }
                    }

                    // Không tìm thấy hoặc voucher không hợp lệ -> Ghi nhận Null Caching 60s
                    redisService.setNullSentinel(RedisTable.VOUCHER_INFO, upperCode, 60);
                    log.debug("🛡️ VOUCHER_NULL_CACHED: Table: VOUCHER_INFO, Key: {} (TTL 60s)", upperCode);
                    return Optional.empty();

                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                // Không giành được lock -> Tạm dừng 50ms và đọc lại cache
                Thread.sleep(50);
                return getValidVoucher(code);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("⚠️ VOUCHER_LOCK_INTERRUPTED for code {}", upperCode);
            return Optional.empty();
        }
    }

    /**
     * Lấy danh sách Voucher hợp lệ từ Redis theo danh sách mã
     */
    public Map<String, VoucherCacheDto> getValidVouchers(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, VoucherCacheDto> result = new HashMap<>();
        for (String code : codes) {
            getValidVoucher(code).ifPresent(v -> result.put(v.getCode().toUpperCase(), v));
        }
        return result;
    }

    /**
     * Xóa Voucher khỏi Redis khi bị hủy / vô hiệu hóa
     */
    public void evictVoucher(String code) {
        if (code != null) {
            String upperCode = code.trim().toUpperCase();
            redisService.unlink(RedisTable.VOUCHER_INFO.key(upperCode));
            log.info("🗑️ VOUCHER_EVICTED_FROM_REDIS: Table: VOUCHER_INFO, Key: {}", upperCode);
        }
    }
}
