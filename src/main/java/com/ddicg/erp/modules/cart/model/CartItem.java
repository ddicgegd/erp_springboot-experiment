package com.ddicg.erp.modules.cart.model;

import com.ddicg.erp.core.common.model.base.IdentityOnly;
import com.ddicg.erp.modules.merchandise.model.Product;
import jakarta.persistence.*;

@Entity
@Table(name = "cart_items", indexes = {
        @Index(name = "idx_cartitem_cart", columnList = "cart_id"),
        @Index(name = "idx_cartitem_sku", columnList = "sku")
})
public class CartItem extends IdentityOnly<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    ShoppingCart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    Product product;

    @Column(name = "sku", nullable = false, length = 100)
    String sku;

    @Column(name = "quantity", nullable = false)
    Integer quantity = 0;

    public CartItem() {}

    public CartItem(ShoppingCart cart, Product product, String sku, Integer quantity) {
        this.cart = cart;
        this.product = product;
        this.sku = sku;
        this.quantity = quantity;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public ShoppingCart getCart() { return cart; }
    public void setCart(ShoppingCart cart) { this.cart = cart; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public static CartItemBuilder builder() { return new CartItemBuilder(); }

    public static class CartItemBuilder {
        private ShoppingCart cart;
        private Product product;
        private String sku;
        private Integer quantity = 0;

        CartItemBuilder() {}

        public CartItemBuilder cart(ShoppingCart cart) { this.cart = cart; return this; }
        public CartItemBuilder product(Product product) { this.product = product; return this; }
        public CartItemBuilder sku(String sku) { this.sku = sku; return this; }
        public CartItemBuilder quantity(Integer quantity) { this.quantity = quantity; return this; }

        public CartItem build() {
            return new CartItem(cart, product, sku, quantity);
        }
    }
}
