package com.ddicg.erp.modules.merchandise.dto.request;

import com.ddicg.erp.core.common.annotation.NormalizedId;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Set;

/**
 * Request DTO để tạo batch Attributes (variants) cho Product thông qua productId.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateAttributesBatchRequest {

    /**
     * Tên sản phẩm chung cho tất cả biến thể.
     */
    @NotBlank(message = "Không được để trống name.")
    String name;

    /**
     * ID của Product chứa variant này (bắt buộc).
     * Hỗ trợ nhiều format: id, productId, product_id.
     * Được normalize tự động: uppercase + remove dashes.
     */
    @NormalizedId
    @JsonProperty("id")
    @JsonAlias({ "productId", "product_id" })
    @NotNull(message = "Không được để trống nguồn sản phẩm")
    String productId;

    /**
     * Từ khóa SEO chung.
     */
    Set<String> keywords;

    /**
     * Danh sách các biến thể cụ thể đã được gán giá và số lượng.
     */
    @NotEmpty(message = "Phải có ít nhất 1 biến thể")
    @Valid
    List<AttributeInput> attributes;
}
