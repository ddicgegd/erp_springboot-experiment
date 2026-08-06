package com.ddicg.erp.service.dto.request;

import com.ddicg.erp.common.annotation.NormalizedId;
import com.ddicg.erp.model.enums.StockStatus;
import com.ddicg.erp.service.dto.PromotionDto;
import com.ddicg.erp.service.dto.SpecificationGroupDto;
import com.ddicg.erp.service.dto.VariantOptionDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Set;

/**
 * Request DTO để cập nhật Attributes (variant).
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateAttributesRequest {

    /**
     * ID (bắt buộc).
     */
    @NormalizedId
    @JsonProperty("id")
    @NotNull(message = "ID không được để trống")
    String id;

    /**
     * Tên mới (optional, tối đa 255 ký tự).
     */
    @JsonProperty("name")
    @Size(max = 255, message = "Tên không được vượt quá 255 ký tự")
    String name;

    /**
     * Giá mới (optional, phải lớn hơn 0).
     */
    @JsonProperty("price")
    @Positive(message = "Giá phải lớn hơn 0")
    Double price;

    /**
     * Giá khuyến mãi mới (optional, phải lớn hơn 0).
     */
    @JsonProperty("sale_price")
    @Positive(message = "Giá khuyến mãi phải lớn hơn 0")
    Double salePrice;

    /**
     * Variant options mới (optional).
     * VD: [{key:"Color", value:"Đen"}, {key:"Size", value:"L"}]
     */
    @JsonProperty("variantOptions")
    @Valid
    List<VariantOptionDto> variantOptions;

    /**
     * Trạng thái sản phẩm (optional).
     */
    @JsonProperty("statusProduct")
    StockStatus status;

    /**
     * Từ khóa SEO mới (optional, tối đa 20 từ khóa, mỗi từ khóa tối đa 50 ký tự).
     */
    @JsonProperty("keywords")
    @Size(max = 20, message = "Số lượng từ khóa không được vượt quá 20")
    Set<@Size(max = 50, message = "Mỗi từ khóa không được vượt quá 50 ký tự") String> keywords;

    /**
     * Dữ liệu specifications mới (optional, tối đa 50 specifications).
     */
    @JsonProperty("specifications")
    @Size(max = 50, message = "Số lượng specifications không được vượt quá 50")
    @Valid
    List<SpecificationGroupDto> specifications;

    /**
     * Dữ liệu promotions mới (optional, tối đa 10 promotions).
     */
    @JsonProperty("promotions")
    @Size(max = 10, message = "Số lượng promotions không được vượt quá 10")
    @Valid
    List<PromotionDto> promotions;
}
