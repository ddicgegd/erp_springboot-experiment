package com.ddicg.erp.service.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

/**
 * Request DTO để tạo Product mới.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateProductRequest {

    @JsonProperty("name")
    @NotBlank(message = "Tên sản phẩm không được để trống.")
    String name;

    @JsonProperty("category_sku")
    @JsonAlias({ "categorySku", "category_sku" })
    @NotNull(message = "Mã dịnh danh của danh mục không được để trống.")
    String categorySku;

    @JsonProperty("status")
    String status;

    /** Phần trăm giảm giá (optional) */
    @JsonProperty("discount_percent")
    Double discountPercent;

    /** Ngày bắt đầu giảm giá (optional) */
    @JsonProperty("discount_start_date")
    LocalDateTime discountStartDate;

    /** Ngày kết thúc giảm giá (optional) */
    @JsonProperty("discount_end_date")
    LocalDateTime discountEndDate;
}
