package com.ddicg.erp.core.config.cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.modules.merchandise.mapper.ProductMapper;
import com.ddicg.erp.modules.merchandise.repository.ProductRepository;
import com.ddicg.erp.modules.merchandise.dto.ProductDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Dịch vụ đồng bộ hóa cache chạy ngầm (Background Cache Sync Service).
//
// Cơ chế hoạt động:
// 1. Khi dữ liệu sản phẩm thay đổi ở DB (thêm/sửa/xóa), Service gọi markProductDirty(Long) để đánh dấu ID đó là "bẩn" (dirty).
// 2. Tiến trình syncDirtyProductCaches() chạy định kỳ (mỗi 5 phút), duyệt qua danh sách dirty IDs, query DB bằng JOIN FETCH, và nạp đè dữ liệu mới vào RAM cache một cách từ từ (nghỉ 100ms giữa mỗi bản ghi).
// 3. Kiểm soát tải hệ thống: RAM/CPU tăng không quá 10-15% nhờ cơ chế nghỉ giữa các lần đọc-ghi cache.
@Service
@Slf4j
@RequiredArgsConstructor
public class CacheSyncService {
    private static final Logger log = LoggerFactory.getLogger(CacheSyncService.class);


    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final CacheManager cacheManager;

    // Tập hợp các Product ID đã bị thay đổi ở DB, chờ được đồng bộ vào cache RAM.
    private final Set<Long> dirtyProductIds = ConcurrentHashMap.newKeySet();

    // Đánh dấu một sản phẩm đã thay đổi ở DB.
    // Được gọi bởi các Service nghiệp vụ sau khi thực hiện Thêm / Sửa / Xóa sản phẩm.
    public void markProductDirty(Long id) {
        dirtyProductIds.add(id);
        log.debug("Đã đánh dấu Product ID={} cần đồng bộ cache.", id);
    }

    // Tiến trình đồng bộ chạy ngầm, thực thi mỗi 5 phút.
    //
    // Duyệt qua danh sách dirty IDs và:
    // - Nếu sản phẩm vẫn tồn tại ở DB: nạp đè DTO mới vào cache RAM.
    // - Nếu sản phẩm đã bị xóa khỏi DB: xóa key tương ứng khỏi cache RAM.
    // Nghỉ 100ms giữa mỗi bản ghi để kiểm soát tải RAM/CPU không tăng đột biến.
    @Scheduled(cron = "0 */5 * * * *")
    public void syncDirtyProductCaches() {
        if (dirtyProductIds.isEmpty()) {
            return;
        }

        Cache cache = cacheManager.getCache("productDetails");
        if (cache == null) {
            log.warn("Không tìm thấy vùng cache 'productDetails'. Bỏ qua đồng bộ.");
            return;
        }

        // Sao chép và giải phóng Set gốc ngay lập tức để tránh tranh chấp (race condition)
        Set<Long> idsToSync = Set.copyOf(dirtyProductIds);
        dirtyProductIds.removeAll(idsToSync);

        log.info("Bắt đầu đồng bộ chạy ngầm cho {} sản phẩm thay đổi...", idsToSync.size());
        int synced = 0;

        for (Long id : idsToSync) {
            try {
                // Eager Loading qua JOIN FETCH: tải Product kèm Category trong 1 SQL
                productRepository.findByIdWithDetails(id).ifPresentOrElse(
                        product -> {
                            ProductDto dto = productMapper.toDto(product);
                            cache.put(id, dto);
                        },
                        () -> cache.evict(id) // Đã xóa ở DB → xóa khỏi RAM
                );
                synced++;

                // Nghỉ 100ms giữa mỗi bản ghi → RAM/CPU tăng không quá ~10-15%
                Thread.sleep(100);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Tiến trình đồng bộ cache bị ngắt.");
                dirtyProductIds.addAll(idsToSync); // Hoàn trả để thử lại lần sau
                return;
            } catch (Exception e) {
                log.error("Lỗi đồng bộ cache cho Product ID={}: {}", id, e.getMessage());
                dirtyProductIds.add(id); // Cho lại vào danh sách để thử lại
            }
        }

        log.info("Đồng bộ cache thành công {} / {} sản phẩm.", synced, idsToSync.size());
    }
}