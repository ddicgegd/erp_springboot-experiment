package com.ddicg.erp.modules.cart.dto;

import com.ddicg.erp.modules.cart.model.CartItem;
import java.io.Serializable;
import java.util.List;

public class ShoppingCartDto implements Serializable {
    private Long id;
    private String name;
    private List<CartItem> items;
    private Integer totalItems;
    private Double totalPrice;
    private Double totalSalePrice;
    private Double totalDiscount;

    public ShoppingCartDto() {}

    public ShoppingCartDto(Long id, String name, List<CartItem> items, Integer totalItems, Double totalPrice, Double totalSalePrice, Double totalDiscount) {
        this.id = id;
        this.name = name;
        this.items = items;
        this.totalItems = totalItems;
        this.totalPrice = totalPrice;
        this.totalSalePrice = totalSalePrice;
        this.totalDiscount = totalDiscount;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<CartItem> getItems() { return items; }
    public Integer getTotalItems() { return totalItems; }
    public Double getTotalPrice() { return totalPrice; }
    public Double getTotalSalePrice() { return totalSalePrice; }
    public Double getTotalDiscount() { return totalDiscount; }

    // Keep CartItemDto for backward compat with Helper.java
    public static class CartItemDto implements Serializable {
        private String sku;
        private Integer quantity;

        public CartItemDto() {}
        public CartItemDto(String sku, Integer quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }

        public String getSku() { return sku; }
        public Integer getQuantity() { return quantity; }
    }
}
