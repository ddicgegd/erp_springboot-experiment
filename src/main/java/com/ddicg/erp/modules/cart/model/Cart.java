package com.ddicg.erp.modules.cart.model;

import com.ddicg.erp.core.common.model.base.IdentityOnly;
import com.ddicg.erp.core.common.model.embedded.AuditEntry;
import com.ddicg.erp.core.config.converter.AuditEntryListConverter;
import com.ddicg.erp.modules.iam.model.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "SHOPPING_CART")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Cart extends IdentityOnly<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    User user;

    @Column(name = "total_items")
    Integer totalItems;

    @Column(name = "total_price")
    Double totalPrice;

    @Column(name = "total_sale_price")
    Double totalSalePrice;

    @Column(name = "total_discount")
    Double totalDiscount;

    @Column(name = "last_activity_at")
    LocalDateTime lastActivityAt;

    @Version
    @Column(name = "version")
    Long version;

    @Column(name = "created_by", updatable = false)
    String createdBy;

    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    String deletedBy;

    @Convert(converter = AuditEntryListConverter.class)
    @Column(name = "update_history", columnDefinition = "CLOB")
    @Builder.Default
    List<AuditEntry> updateHistory = new ArrayList<>();

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    List<CartItem> items = new ArrayList<>();

    @PrePersist
    public void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void onPreUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
