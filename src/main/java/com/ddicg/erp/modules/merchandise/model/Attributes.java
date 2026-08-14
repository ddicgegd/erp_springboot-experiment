package com.ddicg.erp.modules.merchandise.model;

import com.ddicg.erp.core.common.model.base.BaseEntity;
import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.core.common.model.embedded.SpecificationGroup;
import com.ddicg.erp.core.common.model.embedded.VariantOption;
import com.ddicg.erp.core.common.model.embedded.Promotion;
import com.ddicg.erp.core.common.model.embedded.MediaItem;
import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "Attributes", indexes = {
                @Index(name = "idx_attributes_product", columnList = "product_id"),
                @Index(name = "idx_attributes_sku", columnList = "sku_name")
})
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Attributes extends BaseEntity<Long> {

        @Embedded
        @AttributeOverrides({
                        @AttributeOverride(name = "sku", column = @Column(name = "sku_name"))
        })
        @JsonIgnore
        @Builder.Default
        SkuInfo sku = new SkuInfo();

        @Column(name = "name")
        String name;

        @Column(name = "price")
        double price;

        @Column(name = "sale_price")
        double salePrice;

        @Column(name = "cost_price")
        @Builder.Default
        Double costPrice = 0.0;

        @Column(name = "sold_quantity")
        @Builder.Default
        Integer soldQuantity = 0;

        @Column(name = "total_orders")
        @Builder.Default
        Integer totalOrders = 0;

        @Convert(converter = com.ddicg.erp.core.config.converter.VariantOptionListConverter.class)
        @Column(name = "variant_options", columnDefinition = "CLOB")
        @Builder.Default
        List<VariantOption> variantOptions = new ArrayList<>();

        @Column(name = "status_product")
        @Enumerated(EnumType.STRING)
        @Builder.Default
        StockStatus statusProduct = StockStatus.AVAILABLE;

        @ElementCollection(fetch = FetchType.LAZY)
        @CollectionTable(name = "attributes_keywords", joinColumns = @JoinColumn(name = "attributes_id"))
        @Column(name = "keyword", length = 50)
        @Builder.Default
        Set<String> keywords = new HashSet<>();

        @Convert(converter = com.ddicg.erp.core.config.converter.SpecificationGroupListConverter.class)
        @Column(name = "specifications", columnDefinition = "CLOB")
        @Builder.Default
        List<SpecificationGroup> specifications = new ArrayList<>();

        @Convert(converter = com.ddicg.erp.core.config.converter.PromotionListConverter.class)
        @Column(name = "promotions", columnDefinition = "CLOB")
        @Builder.Default
        List<Promotion> promotions = new ArrayList<>();

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "product_id")
        @OnDelete(action = OnDeleteAction.CASCADE)
        Product product;
}
