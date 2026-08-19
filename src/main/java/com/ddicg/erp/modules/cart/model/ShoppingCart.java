package com.ddicg.erp.modules.cart.model;

import com.ddicg.erp.core.common.model.base.BaseEntity;
import com.ddicg.erp.modules.iam.model.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shopping_carts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShoppingCart extends BaseEntity<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<CartItem> items = new ArrayList<>();

    @Column(name = "last_activity_at")
    LocalDateTime lastActivityAt;

    public ShoppingCart(User user) {
        this.user = user;
        this.items = new ArrayList<>();
        this.lastActivityAt = LocalDateTime.now();
    }

    public void addItem(CartItem item) {
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(item);
        item.setCart(this);
    }

    public void removeItem(CartItem item) {
        if (items != null) {
            items.remove(item);
            item.setCart(null);
        }
    }

    public void clear() {
        if (items != null) {
            items.clear();
        }
    }
}
