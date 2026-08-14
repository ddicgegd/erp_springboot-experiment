package com.ddicg.erp.modules.merchandise.dto.response;

import com.ddicg.erp.modules.merchandise.dto.MediaItemDto;

import java.io.Serializable;
import java.util.List;

/**
 * Record hiển thị thông tin sản phẩm ra giao diện người dùng (Customer UI).
 *
 * <p>Chỉ chứa các trường công khai, an toàn để hiển thị ra bên ngoài.
 * Các trường nhạy cảm nội bộ như {@code id}, {@code totalRevenue}, {@code totalSoldQuantity},
 * {@code viewCount}, {@code totalOrders} bị ẩn hoàn toàn.
 *
 * <p>Sử dụng {@code sku} thay thế {@code id} làm định danh công khai
 * để tránh lộ ID tuần tự của database.
 */
public record ProductPublicRecord(
        String sku,
        String name,
        List<MediaItemDto> mediaItems,
        String categoryName,
        String status
) implements Serializable {}
