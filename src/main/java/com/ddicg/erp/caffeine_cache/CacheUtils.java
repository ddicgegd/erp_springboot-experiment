package com.ddicg.erp.caffeine_cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

public final class CacheUtils {

    private CacheUtils() {
        // Prevent instantiation
    }

    /**
     * Tự động lấy dữ liệu từ cache Caffeine theo lô, 
     * nếu thiếu phần tử nào (Cache Miss) sẽ dùng dbLoader để nạp từ DB và tự động ghi đè lại cache.
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> getAll(CacheManager cacheManager, 
                                          String cacheName, 
                                          Collection<K> keys, 
                                          Function<Collection<K>, Map<K, V>> dbLoader) {
        Cache cache = cacheManager.getCache(cacheName);
        
        if (cache != null && cache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache) {
            var nativeCache = (com.github.benmanes.caffeine.cache.Cache<K, V>) cache.getNativeCache();
            
            return nativeCache.getAll(keys, missingKeys -> {
                Collection<K> missingList = new ArrayList<>();
                missingKeys.forEach(missingList::add);
                return dbLoader.apply(missingList);
            });
        }
        
        // Fallback gọi trực tiếp DB khi không có cache
        return dbLoader.apply(keys);
    }
}