package com.ddicg.erp.service.dto;

import com.ddicg.erp.model.entity.Product;
import com.ddicg.erp.model.enums.ActiveStatus;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO for {@link Product}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto implements Serializable {

    Long id;

    String name;

    SkuInfoDto skuInfo;

    List<MediaItemDto> mediaItems;

    /** Trang thai san pham */
    ActiveStatus status;

    Integer viewCount;

    /** Tong so luong da ban */
    Integer totalSoldQuantity;

    /** Tong doanh thu */
    java.math.BigDecimal totalRevenue;

    /** Phần trăm giảm giá */
    Double discountPercent;

    /** Ngày bắt đầu giảm giá */
    LocalDateTime discountStartDate;

    /** Ngày kết thúc giảm giá */
    LocalDateTime discountEndDate;

    /** Tên danh mục (snapshot) */
    String categoryName;
}
