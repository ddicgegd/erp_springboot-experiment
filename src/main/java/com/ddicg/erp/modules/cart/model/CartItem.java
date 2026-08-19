package com.ddicg.erp.modules.cart.model;

import com.ddicg.erp.core.common.model.base.IdentityOnly;
import com.ddicg.erp.modules.merchandise.model.Product;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "cart_items", indexes = {
        @Index(name = "idx_cartitem_cart", columnList = "cart_id"),
        @Index(name = "idx_cartitem_sku", columnList = "sku")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
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
    @Builder.Default
    Integer quantity = 0;
}
