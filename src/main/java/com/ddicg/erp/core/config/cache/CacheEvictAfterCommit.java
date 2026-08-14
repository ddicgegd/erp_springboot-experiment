package com.ddicg.erp.core.config.cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Utility giúp clear cache SAU KHI transaction commit thành công.
 * <p>
 * Tránh race condition: nếu clear cache trước commit, một thread khác có thể
 * đọc dữ liệu cũ (chưa commit) và cache lại → dữ liệu stale đến hết TTL.
 * <p>
 * Cách dùng trong method có @Transactional:
 * <pre>{@code
 * cacheEvictAfterCommit.allEntries("attributes");
 * cacheEvictAfterCommit.key("productDetails", productId);
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictAfterCommit {


    private final CacheManager cacheManager;

    /**
     * Clear toàn bộ cache SAU KHI transaction commit.
     *
     * @param cacheName tên vùng cache (vd: "attributes", "categoryDetails")
     */
    public void allEntries(String cacheName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Không có transaction active → clear ngay
            clearCacheNow(cacheName);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                clearCacheNow(cacheName);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    log.warn("Transaction không commit (status={}), bỏ qua clear cache '{}'", status, cacheName);
                }
            }
        });
    }

    /**
     * Xoá 1 key cụ thể khỏi cache SAU KHI transaction commit.
     *
     * @param cacheName tên vùng cache
     * @param key       key cần xoá
     */
    public void key(String cacheName, Object key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Không có transaction → evict ngay
            evictKeyNow(cacheName, key);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictKeyNow(cacheName, key);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    log.warn("Transaction không commit (status={}), bỏ qua evict cache '{}' key={}", status, cacheName, key);
                }
            }
        });
    }

    private void clearCacheNow(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.debug("Cleared cache '{}'", cacheName);
        }
    }

    private void evictKeyNow(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            log.debug("Evicted cache '{}' key={}", cacheName, key);
        }
    }
}