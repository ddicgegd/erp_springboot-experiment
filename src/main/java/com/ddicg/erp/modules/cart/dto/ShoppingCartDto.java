package com.ddicg.erp.modules.cart.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShoppingCartDto implements Serializable {
    Long id;
    String username;
    List<CartItemResponse> items;
    Integer totalItems;
    Double totalPrice;
    Double totalSalePrice;
    Double totalDiscount;
    Double finalAmount;
}
