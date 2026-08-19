package com.ddicg.erp.modules.cart.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}
