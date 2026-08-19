package com.ddicg.erp.modules.order.service.discount;

import com.ddicg.erp.modules.order.dto.VoucherCacheDto;
import com.ddicg.erp.modules.order.model.Voucher;
import com.ddicg.erp.modules.order.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherRedisService {

    private final VoucherRepository voucherRepository;
    private final RedissonClient redissonClient;

    public static final String VOUCHER_INFO_PREFIX = "voucher:info:";

    /**
     * Nạp Voucher lên Redis với TTL = (endDate - now)
     */
    public void cacheVoucher(Voucher voucher) {
        if (voucher == null || voucher.getCode() == null || voucher.getEndDate() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        long ttlSeconds = Duration.between(now, voucher.getEndDate()).getSeconds();

        if (ttlSeconds > 0 && voucher.isCurrentlyValid()) {
            String key = VOUCHER_INFO_PREFIX + voucher.getCode().toUpperCase();
            RBucket<VoucherCacheDto> bucket = redissonClient.getBucket(key);
            bucket.set(VoucherCacheDto.fromEntity(voucher), Duration.ofSeconds(ttlSeconds));
            log.info("⚡ VOUCHER_CACHED_ON_REDIS: Key: {} (TTL: {}s)", key, ttlSeconds);
        }
    }

    /**
     * Lấy Voucher hợp lệ từ Redis (Cache-aside: đọc Redis trước, fallback DB)
     */
    public Optional<VoucherCacheDto> getValidVoucher(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        String upperCode = code.trim().toUpperCase();
        String key = VOUCHER_INFO_PREFIX + upperCode;
        RBucket<VoucherCacheDto> bucket = redissonClient.getBucket(key);
        VoucherCacheDto cached = bucket.get();

        if (cached != null) {
            return cached.isCurrentlyValid() ? Optional.of(cached) : Optional.empty();
        }

        // Cache miss -> Fallback DB
        Optional<Voucher> dbVoucherOpt = voucherRepository.findByCode(upperCode);
        if (dbVoucherOpt.isPresent()) {
            Voucher dbVoucher = dbVoucherOpt.get();
            if (dbVoucher.isCurrentlyValid()) {
                cacheVoucher(dbVoucher);
                return Optional.of(VoucherCacheDto.fromEntity(dbVoucher));
            }
        }

        return Optional.empty();
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
            String key = VOUCHER_INFO_PREFIX + code.trim().toUpperCase();
            redissonClient.getBucket(key).delete();
            log.info("🗑️ VOUCHER_EVICTED_FROM_REDIS: Key: {}", key);
        }
    }
}
