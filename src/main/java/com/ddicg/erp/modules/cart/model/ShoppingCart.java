package com.ddicg.erp.modules.cart.model;

import com.ddicg.erp.core.common.model.base.BaseEntity;
import com.ddicg.erp.modules.iam.model.User;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shopping_carts")
public class ShoppingCart extends BaseEntity<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    List<CartItem> items = new ArrayList<>();

    public ShoppingCart() {}

    public ShoppingCart(User user) {
        this.user = user;
    }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }

    public List<CartItem> getCartItems() { return items; }

    public Integer getTotalItems() { return items != null ? items.size() : 0; }

    public void addItem(CartItem item) {
        items.add(item);
        item.setCart(this);
    }

    public void removeItem(CartItem item) {
        items.remove(item);
        item.setCart(null);
    }

    public void clear() {
        items.clear();
    }

    public void addItem(String sku, int quantity) {}
    public void removeItemBySku(String sku) {}
    public void updateTotals(int count, double total, double sale) {}
    public com.ddicg.erp.core.common.model.embedded.AuditInfo getAuditInfo() { return new com.ddicg.erp.core.common.model.embedded.AuditInfo(); }
    public void setAuditInfo(com.ddicg.erp.core.common.model.embedded.AuditInfo auditInfo) {}
    public Double getTotalPrice() { return 0.0; }
    public Double getTotalSalePrice() { return 0.0; }
    public Double getTotalDiscount() { return 0.0; }


    public void clearItems() { if (items != null) items.clear(); }
}
