package com.ddicg.erp.caffeine_cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Cấu hình Spring Cache với Caffeine (in-memory cache).
 * <p>
 * Chiến lược cache (tối ưu cho hiệu năng đa người dùng):
 * <ul>
 *   <li><b>productDetails</b>: chi tiết sản phẩm (TTL 10 phút, max 1000) — đọc nhiều, ghi ít</li>
 *   <li><b>products</b>: kết quả tìm kiếm (TTL 10 phút, max 500) — dự phòng, hiện tại search query trực tiếp</li>
 *   <li><b>categoryDetails</b>: chi tiết danh mục (TTL 30 phút, max 200) — thay đổi rất hiếm</li>
 *   <li><b>attributes</b>: thuộc tính sản phẩm (TTL 5 phút, max 2000) — thay đổi qua mutation có @CacheEvict</li>
 * </ul>
 * <p>
 * Nguyên tắc tránh cache stampede khi có nhiều user:
 * <ul>
 *   <li>Không dùng @Cacheable cho search (key hashcode vô dụng, gây memory waste)</li>
 *   <li>Category update KHÔNG clear product cache (tránh cache miss hàng loạt)</li>
 *   <li>Update product dùng targeted eviction + async Redis Stream (không allEntries)</li>
 *   <li>Caching dạng bulk qua CacheUtils.getAll() chỉ load những ID miss từ DB</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // Constants định nghĩa tên các thùng chứa cache
    public static final String CACHE_PRODUCTS = "products";
    public static final String CACHE_PRODUCT_DETAILS = "productDetails";
    public static final String CACHE_CATEGORY_DETAILS = "categoryDetails";
    public static final String CACHE_ATTRIBUTES = "attributes";

    private record CacheSpec(String name, Duration ttl, long maxSize) {}

    // Danh sách cấu hình các thùng chứa cache
    private static final List<CacheSpec> CACHE_SPECS = List.of(
            new CacheSpec(CACHE_PRODUCTS, Duration.ofMinutes(10), 500),
            new CacheSpec(CACHE_PRODUCT_DETAILS, Duration.ofMinutes(10), 1000),
            new CacheSpec(CACHE_CATEGORY_DETAILS, Duration.ofMinutes(30), 200),
            new CacheSpec(CACHE_ATTRIBUTES, Duration.ofMinutes(5), 2000)
    );

    @Bean
    public CacheManager cacheManager() {
        var cacheManager = new CaffeineCacheManager();
        
        CACHE_SPECS.forEach(spec -> cacheManager.registerCustomCache(
                spec.name(),
                Caffeine.newBuilder()
                        .expireAfterWrite(spec.ttl())
                        .maximumSize(spec.maxSize())
                        .recordStats()
                        .build()
        ));
        
        return cacheManager;
    }
}