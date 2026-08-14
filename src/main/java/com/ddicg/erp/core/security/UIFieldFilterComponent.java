package com.ddicg.erp.core.security;

import com.ddicg.erp.modules.merchandise.dto.ProductDto;
import com.ddicg.erp.modules.merchandise.dto.response.ProductPublicRecord;
import org.springframework.stereotype.Component;

// Component lọc và chuyển đổi dữ liệu từ DTO nội bộ sang Record công khai.
//
// Vai trò:
// - Nhận ProductDto đầy đủ từ Cache RAM (chứa tất cả các trường kể cả nội bộ).
// - Map sang ProductPublicRecord chỉ chứa các trường hiển thị công khai cho giao diện người dùng (Customer UI).
//
// Được inject vào các Controller phục vụ luồng GraphQL / Customer API.
// Các REST Controller nội bộ (Admin) không cần dùng Component này.
@Component
public class UIFieldFilterComponent {

    // Chuyển đổi ProductDto sang ProductPublicRecord.
    // Các trường nhạy cảm (id, totalRevenue, viewCount,...) bị ẩn.
    public ProductPublicRecord toPublicRecord(ProductDto dto) {
        if (dto == null) return null;

        return new ProductPublicRecord(
                dto.getSkuInfo() != null ? dto.getSkuInfo().getSku() : null,
                dto.getName(),
                dto.getMediaItems(),
                dto.getCategoryName(),
                dto.getStatus() != null ? dto.getStatus().name() : null
        );
    }
}
