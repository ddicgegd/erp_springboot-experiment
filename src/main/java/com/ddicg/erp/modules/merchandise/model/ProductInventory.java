package com.ddicg.erp.modules.merchandise.model;

import com.ddicg.erp.core.common.model.base.IdentityOnly;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "product_inventory", indexes = {
    @Index(name = "idx_inventory_sku", columnList = "sku", unique = true)
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductInventory extends IdentityOnly<Long> {
    
    @Column(nullable = false, unique = true)
    String sku;
    
    @Column(nullable = false)
    Integer availableQuantity;
    
    @Column(nullable = false)
    @Builder.Default
    Integer reservedQuantity = 0;
    
    @Version
    Long version;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    Product product;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Integer getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }
    public Integer getReservedQuantity() { return reservedQuantity != null ? reservedQuantity : 0; }
    public void setReservedQuantity(Integer reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
}
