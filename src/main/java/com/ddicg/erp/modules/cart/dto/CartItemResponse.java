package com.ddicg.erp.modules.cart.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CartItemResponse implements Serializable {
    String sku;
    String productName;
    String imageUrl;
    String attributesTitle;
    Double unitPrice;
    Double salePrice;
    Integer quantity;
    Double subTotal;
    Boolean isAvailable;
    Integer stock;
    Object specifications;
    Object promotions;
}
