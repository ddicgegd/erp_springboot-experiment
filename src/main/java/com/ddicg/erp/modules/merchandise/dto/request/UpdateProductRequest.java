package com.ddicg.erp.modules.merchandise.dto.request;

import com.ddicg.erp.core.common.annotation.NormalizedId;
import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 * Request DTO để cập nhật thông tin Product.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateProductRequest {

    @NotNull(message = "SKU sản phẩm không được để trống")
    String sku;

    /**
     * Tên mới của sản phẩm (optional).
     */
    String name;

    /**
     * SKU của Category mới (optional).
     */
    @NormalizedId
    String categorySku;

    /**
     * Trạng thái active của sản phẩm (optional).
     */
    ActiveStatus status;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getCategorySku() { return categorySku; }
    public void setCategorySku(String categorySku) { this.categorySku = categorySku; }

}
