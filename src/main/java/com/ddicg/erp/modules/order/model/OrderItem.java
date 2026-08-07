package com.ddicg.erp.modules.order.model;

import com.ddicg.erp.core.common.model.base.IdentityOnly;
import com.ddicg.erp.core.common.model.embedded.VariantOption;
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
        @Index(name = "idx_orderitem_attributes_sku", columnList = "attributes_sku")
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

    @Column(name = "product_name", nullable = false, length = 500)
    String productName;

    @Column(name = "product_sku", length = 100)
    String productSku;

    @Column(name = "attributes_sku", nullable = false, length = 100)
    String attributesSku;

    @Column(name = "attributes_name", length = 500)
    String attributesName;

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

    @Column(name = "cost_price")
    @Builder.Default
    Double costPrice = 0.0;

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
}

