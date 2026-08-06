package com.ddicg.erp.service.dto;

import lombok.Value;

import java.io.Serializable;
import java.util.List;

/**
 * DTO for {@link com.ddicg.erp.model.entity.ShoppingCart}
 */
@Value
public class ShoppingCartDto implements Serializable {
    Long id;
    String name;
    List<CartItemDto> items;
    Integer totalItems;
    Double totalPrice;
    Double totalSalePrice;
    Double totalDiscount;

    @Value
    public static class CartItemDto {
        String sku;
        Integer quantity;
    }
}
