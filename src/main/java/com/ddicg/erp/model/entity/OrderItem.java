package com.ddicg.erp.model.entity;

import com.ddicg.erp.model.base.IdentityOnly;
import com.ddicg.erp.model.embedded.VariantOption;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
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
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderItem extends IdentityOnly<Long> {

    /**
     * Đơn hàng liên kết
     * @en Linked order
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "FK_order_item_order"))
    @ToString.Exclude
    @OnDelete(action = OnDeleteAction.CASCADE)
    Order order;

    /**
     * Sản phẩm liên kết
     * @en Linked product
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "FK_order_item_product"))
    @ToString.Exclude
    Product product;

    /**
     * Thuộc tính sản phẩm
     * @en Product attributes
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attributes_id", nullable = false, foreignKey = @ForeignKey(name = "FK_order_item_attributes"))
    @ToString.Exclude
    Attributes attributes;

    /**
     * Tên sản phẩm
     * @en Product name
     */
    @Column(name = "product_name", nullable = false, length = 500)
    String productName;

    /**
     * SKU sản phẩm
     * @en Product SKU
     */
    @Column(name = "product_sku", length = 100)
    String productSku;

    /**
     * SKU thuộc tính
     * @en Attributes SKU
     */
    @Column(name = "attributes_sku", length = 100)
    String attributesSku;

    /**
     * Các tùy chọn biến thể
     * @en Variant options
     */
    @Convert(converter = com.ddicg.erp.config.converter.VariantOptionListConverter.class)
    @Column(name = "variant_options", columnDefinition = "CLOB")
    @Builder.Default
    List<VariantOption> variantOptions = new ArrayList<>();

    /**
     * Số lượng
     * @en Quantity
     */
    @Column(name = "quantity", nullable = false)
    Integer quantity;

    /**
     * Đơn giá
     * @en Unit price
     */
    @Column(name = "unit_price", nullable = false)
    Double unitPrice;

    /**
     * Giá bán
     * @en Sale price
     */
    @Column(name = "sale_price", nullable = false)
    Double salePrice;

    /**
     * Số tiền giảm giá
     * @en Discount amount
     */
    @Column(name = "discount_amount")
    @Builder.Default
    Double discountAmount = 0.0;

    /**
     * Phần trăm giảm giá
     * @en Discount percentage
     */
    @Column(name = "discount_percentage")
    @Builder.Default
    Double discountPercentage = 0.0;

    /**
     * Tổng tiền hàng
     * @en Subtotal
     */
    @Column(name = "subtotal", nullable = false)
    Double subtotal;

    /**
     * Số tiền thuế
     * @en Tax amount
     */
    @Column(name = "tax_amount")
    @Builder.Default
    Double taxAmount = 0.0;

    /**
     * Ảnh sản phẩm
     * @en Image URL
     */
    @Column(name = "image_url", length = 500)
    String imageUrl;

    /**
     * Tính toán subtotal dựa trên quantity, salePrice và discount
     */
    public void calculateSubtotal() {
        if (quantity != null && salePrice != null) {
            double total = quantity * salePrice;
            if (discountAmount != null) {
                total -= discountAmount;
            }
            this.subtotal = Math.max(0, total);
        }
    }
}
