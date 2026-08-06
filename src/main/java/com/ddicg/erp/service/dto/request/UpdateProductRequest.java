package com.ddicg.erp.service.dto.request;

import com.ddicg.erp.common.annotation.NormalizedId;
import com.ddicg.erp.model.enums.ActiveStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;

/**
 * Request DTO để cập nhật thông tin Product.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateProductRequest {

    /**
     * ID của Product cần cập nhật (bắt buộc).
     * Được normalize tự động: uppercase + remove dashes.
     */
    @NormalizedId
    @NotNull(message = "ID sản phẩm không được để trống")
    String id;

    /**
     * Tên mới của sản phẩm (optional).
     */
    String name;

    /**
     * Id của Category mới (optional).
     */
    @NormalizedId
    String categoryId;

    /**
     * Trạng thái active của sản phẩm (optional).
     */
    ActiveStatus status;

    /** Phần trăm giảm giá mới (optional) */
    Double discountPercent;

    /** Ngày bắt đầu giảm giá mới (optional) */
    LocalDateTime discountStartDate;

    /** Ngày kết thúc giảm giá mới (optional) */
    LocalDateTime discountEndDate;
}
