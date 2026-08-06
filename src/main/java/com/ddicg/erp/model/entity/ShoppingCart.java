package com.ddicg.erp.model.entity;

import com.ddicg.erp.model.base.IdentityOnly;
import com.ddicg.erp.model.embedded.AuditInfo;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shopping_cart", indexes = {
        @Index(name = "idx_shoppingcart_user", columnList = "user_id", unique = true)
})
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShoppingCart extends IdentityOnly<Long> {

    @Embedded
    @Builder.Default
    AuditInfo auditInfo = new AuditInfo();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    User user;

    /**
     * Danh sách sản phẩm trong giỏ — dùng entity thay vì CLOB JSON.
     * 
     * @en Cart items as entities (replaces CLOB JSON).
     */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    List<CartItem> cartItems = new ArrayList<>();

    @Column(name = "total_items")
    @Builder.Default
    Integer totalItems = 0;

    @Column(name = "total_price")
    @Builder.Default
    Double totalPrice = 0.0;

    @Column(name = "total_sale_price")
    @Builder.Default
    Double totalSalePrice = 0.0;

    @Column(name = "total_discount")
    @Builder.Default
    Double totalDiscount = 0.0;

    @Column(name = "last_activity_at")
    LocalDateTime lastActivityAt;

    public void addItem(String sku, int quantity) {
        if (cartItems == null) cartItems = new ArrayList<>();
        cartItems.stream()
                .filter(ci -> ci.getSku().equals(sku))
                .findFirst()
                .ifPresentOrElse(
                        ci -> ci.setQuantity(ci.getQuantity() + quantity),
                        () -> cartItems.add(CartItem.builder()
                                .cart(this)
                                .sku(sku)
                                .quantity(quantity)
                                .build()));
    }

    public void removeItemBySku(String sku) {
        if (cartItems == null) return;
        cartItems.removeIf(ci -> ci.getSku().equals(sku));
    }

    public void updateTotals(Integer totalItems, Double totalPrice, Double totalSalePrice) {
        this.totalItems = totalItems != null ? totalItems : 0;
        this.totalPrice = totalPrice != null ? totalPrice : 0.0;
        this.totalSalePrice = totalSalePrice != null ? totalSalePrice : 0.0;
        this.totalDiscount = this.totalPrice - this.totalSalePrice;
        touchActivity();
    }

    public void clearItems() {
        if (cartItems != null) cartItems.clear();
    }

    public void touchActivity() {
        this.lastActivityAt = LocalDateTime.now();
    }
}
