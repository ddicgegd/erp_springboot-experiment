package com.ddicg.erp.modules.cart.model;

import com.ddicg.erp.core.common.model.base.IdentityOnly;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "CART_ITEMS")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CartItem extends IdentityOnly<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    Cart cart;

    @Column(name = "sku", nullable = false, length = 400)
    String sku;

    @Column(name = "quantity", nullable = false)
    Integer quantity;

    @Column(name = "product_id")
    Long productId;
}
