package com.ddicg.erp.service.dto.request;

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
 * Request DTO để tạo danh sách biến thể cụ thể cho Product.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateAttributesRequest {

    /**
     * Tên sản phẩm chung cho tất cả biến thể.
     */
    @NotBlank(message = "Không được để trống name.")
    String name;

    /**
     * Sku của Product chứa variant này (bắt buộc).
     */
    @JsonProperty("product_sku")
    @JsonAlias({ "productSku", "product_sku" })
    @NotNull(message = "Không được để trống nguồn mã sản phẩm")
    String productSku;

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
