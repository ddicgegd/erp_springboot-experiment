package com.ddicg.erp.modules.order.dto;

import com.ddicg.erp.modules.merchandise.dto.VariantOptionDto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderItemDto {
    Long id;
    Long orderId;
    Long productId;
    Long attributesId;
    String productName;
    String productSku;
    String attributesSku;
    List<VariantOptionDto> variantOptions;
    Integer quantity;
    Double unitPrice;
    Double salePrice;
    Double discountAmount;
    Double discountPercentage;
    Double subtotal;
    Double taxAmount;
    String notes;
    String imageUrl;
}
