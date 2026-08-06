package com.ddicg.erp.model.entity;

import com.ddicg.erp.model.base.IdentityOnly;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Mỗi dòng trong giỏ hàng — thay thế CLOB JSON items.
 * 
 * @en Individual cart item — replaces CLOB JSON items.
 */
@Entity
@Table(name = "cart_items", indexes = {
        @Index(name = "idx_cartitem_cart", columnList = "cart_id"),
        @Index(name = "idx_cartitem_sku", columnList = "sku")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CartItem extends IdentityOnly<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    ShoppingCart cart;

    @Column(name = "sku", nullable = false, length = 100)
    String sku;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    Integer quantity = 0;
}
