package com.ddicg.erp.modules.order.service.discount;

import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.modules.order.model.Voucher;
import com.ddicg.erp.modules.order.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherReservationService {

    private final VoucherRepository voucherRepository;
    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "voucher:lock:";
    private static final String HOLD_PREFIX = "voucher:hold:";
    private static final long LOCK_WAIT_TIME = 5;
    private static final long LOCK_LEASE_TIME = 10;
    public static final int HOLD_TTL_SECONDS = 180; // 3 phút theo Gateway Session

    /**
     * Tạm giữ voucher trong Redis với TTL 3 phút (Soft Reservation) chống race condition
     */
    @Transactional(readOnly = true)
    public void reserveVouchers(List<String> codes, String orderNumber, Double orderSubtotal) {
        if (codes == null || codes.isEmpty()) {
            return;
        }

        List<String> distinctCodes = codes.stream().filter(Objects::nonNull).map(String::trim).distinct().toList();
        List<RLock> acquiredLocks = new ArrayList<>();

        try {
            List<String> sortedCodes = new ArrayList<>(distinctCodes);
            Collections.sort(sortedCodes);

            // 1. Acquire Distributed Locks
            for (String code : sortedCodes) {
                RLock lock = redissonClient.getLock(LOCK_PREFIX + code);
                boolean acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
                if (!acquired) {
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Hệ thống đang xử lý voucher: " + code + ", vui lòng thử lại");
                }
                acquiredLocks.add(lock);
            }

            // 2. Validate & Reserve vào Redis
            for (String code : sortedCodes) {
                Voucher voucher = voucherRepository.findByCode(code)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "Mã voucher không tồn tại: " + code));

                if (!voucher.isCurrentlyValid()) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST, "Mã voucher " + code + " đã hết hạn hoặc hết lượt");
                }

                if (voucher.getMinOrderAmount() != null && orderSubtotal != null && orderSubtotal < voucher.getMinOrderAmount()) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST,
                            "Đơn hàng chưa đạt giá trị tối thiểu " + voucher.getMinOrderAmount() + "đ để áp dụng voucher " + code);
                }

                // Lưu key tạm giữ vào Redis với TTL 3 phút
                String holdKey = HOLD_PREFIX + orderNumber + ":" + code;
                RBucket<String> bucket = redissonClient.getBucket(holdKey);
                bucket.set("RESERVED", Duration.ofSeconds(HOLD_TTL_SECONDS));
                log.info("⚡ REDIS_VOUCHER_HOLD: Key: {} (TTL: {}s)", holdKey, HOLD_TTL_SECONDS);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Gián đoạn khi khóa voucher, vui lòng thử lại");
        } finally {
            for (RLock lock : acquiredLocks) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    log.error("Error unlocking voucher: {}", lock.getName(), e);
                }
            }
        }
    }

    /**
     * Xác nhận sử dụng voucher (Commit khi thanh toán thành công): Tăng usedQuantity trong DB & Xóa Redis Hold
     */
    @Transactional
    public void commitVouchers(List<String> codes, String orderNumber) {
        if (codes == null || codes.isEmpty()) return;

        for (String code : codes) {
            voucherRepository.findByCode(code).ifPresent(voucher -> {
                voucher.setUsedQuantity((voucher.getUsedQuantity() != null ? voucher.getUsedQuantity() : 0) + 1);
                voucherRepository.save(voucher);

                // Xóa key hold trong Redis
                String holdKey = HOLD_PREFIX + orderNumber + ":" + code;
                redissonClient.getBucket(holdKey).delete();
                log.info("🎯 VOUCHER_COMMITTED_IN_DB: Code: {} | Order: {}", code, orderNumber);
            });
        }
    }

    /**
     * Giải phóng voucher khi khách hủy đơn
     */
    public void releaseVouchers(List<String> codes, String orderNumber) {
        if (codes == null || codes.isEmpty()) return;

        for (String code : codes) {
            String holdKey = HOLD_PREFIX + orderNumber + ":" + code;
            redissonClient.getBucket(holdKey).delete();
            log.info("🔓 REDIS_VOUCHER_RELEASED: Key: {}", holdKey);
        }
    }
}
