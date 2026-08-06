package com.ddicg.erp.caffeine_cache;

import com.ddicg.erp.service.Merchandise.CategoryService;
import com.ddicg.erp.service.Merchandise.ProductService;
import com.ddicg.erp.service.dto.request.CategorySearchRequest;
import com.ddicg.erp.service.dto.request.GetProductRequest;
import com.ddicg.erp.service.dto.request.PagingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Tự động nạp trước (Warm-up) dữ liệu vào RAM Cache khi ứng dụng khởi động thành công.
//
// Chiến lược:
// - Category: Nạp toàn bộ (số lượng nhỏ, thay đổi ít, cần tốc độ cao).
// - Product: Nạp trước các sản phẩm phổ biến nhất (trang đầu tiên, sắp xếp theo lượt xem hoặc doanh thu).
// - Attributes: KHÔNG nạp khi startup (dữ liệu rất lớn). Sử dụng Lazy Loading — được nạp vào cache khi có request gọi đến và tự hết hạn sau 5 phút.
@Component
@Slf4j
@RequiredArgsConstructor
public class CacheWarmupListener {

    private final CategoryService categoryService;
    private final ProductService productService;

    @EventListener(ApplicationReadyEvent.class)
    public void warmupCaches() {
        log.info("=== Bắt đầu Warm-up Cache (Category + Product) ===");
        warmupCategories();
        warmupProducts();
        log.info("=== Warm-up Cache hoàn tất ===");
    }

    // Nạp toàn bộ danh mục vào RAM (tối đa 200 bản ghi).
    private void warmupCategories() {
        try {
            CategorySearchRequest request = new CategorySearchRequest();
            PagingRequest paging = new PagingRequest();
            paging.setPage(1);
            paging.setSize(200);
            request.setPaging(paging);

            long count = categoryService.search(request).getTotalElements();
            log.info("Warm-up Category thành công: {} danh mục đã nạp vào RAM.", count);
        } catch (Exception e) {
            log.error("Warm-up Category thất bại: {}", e.getMessage(), e);
        }
    }

    // Nạp trước 500 sản phẩm đầu tiên vào RAM.
    // Sau này có thể tinh chỉnh để chỉ warm-up các sản phẩm "hot" (lượt xem cao nhất).
    private void warmupProducts() {
        try {
            GetProductRequest request = new GetProductRequest();
            PagingRequest paging = new PagingRequest();
            paging.setPage(1);
            paging.setSize(500);
            request.setPaging(paging);

            long count = productService.searchProducts(request).getTotalElements();
            log.info("Warm-up Product thành công: {} sản phẩm đã nạp vào RAM.", count);
        } catch (Exception e) {
            log.error("Warm-up Product thất bại: {}", e.getMessage(), e);
        }
    }
}
