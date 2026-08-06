package com.ddicg.erp.modules.order.model;

import com.ddicg.erp.core.common.model.base.IdentityOnly;
import com.ddicg.erp.core.common.model.embedded.VariantOption;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.merchandise.model.Product;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_orderitem_id", columnList = "id", unique = true),
        @Index(name = "idx_orderitem_order", columnList = "order_id"),
        @Index(name = "idx_orderitem_product", columnList = "product_id"),
        @Index(name = "idx_orderitem_attributes", columnList = "attributes_id")
})
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderItem extends IdentityOnly<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "FK_order_item_order"))
    @ToString.Exclude
    @OnDelete(action = OnDeleteAction.CASCADE)
    Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "FK_order_item_product"))
    @ToString.Exclude
    Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attributes_id", nullable = false, foreignKey = @ForeignKey(name = "FK_order_item_attributes"))
    @ToString.Exclude
    Attributes attributes;

    @Column(name = "product_name", nullable = false, length = 500)
    String productName;

    @Column(name = "product_sku", length = 100)
    String productSku;

    @Column(name = "attributes_sku", length = 100)
    String attributesSku;

    @Convert(converter = com.ddicg.erp.core.config.converter.VariantOptionListConverter.class)
    @Column(name = "variant_options", columnDefinition = "CLOB")
    @Builder.Default
    List<VariantOption> variantOptions = new ArrayList<>();

    @Column(name = "quantity", nullable = false)
    Integer quantity;

    @Column(name = "unit_price", nullable = false)
    Double unitPrice;

    @Column(name = "sale_price", nullable = false)
    Double salePrice;

    @Column(name = "discount_amount")
    @Builder.Default
    Double discountAmount = 0.0;

    @Column(name = "discount_percentage")
    @Builder.Default
    Double discountPercentage = 0.0;

    @Column(name = "subtotal", nullable = false)
    Double subtotal;

    @Column(name = "tax_amount")
    @Builder.Default
    Double taxAmount = 0.0;

    @Column(name = "image_url", length = 500)
    String imageUrl;

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Attributes getAttributes() { return attributes; }
    public void setAttributes(Attributes attributes) { this.attributes = attributes; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductSku() { return productSku; }
    public void setProductSku(String productSku) { this.productSku = productSku; }
    public String getAttributesSku() { return attributesSku; }
    public void setAttributesSku(String attributesSku) { this.attributesSku = attributesSku; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
    public Double getSalePrice() { return salePrice; }
    public void setSalePrice(Double salePrice) { this.salePrice = salePrice; }
    public Double getSubtotal() { return subtotal != null ? subtotal : 0.0; }
    public void setSubtotal(Double subtotal) { this.subtotal = subtotal; }
}
