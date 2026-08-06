package com.ddicg.erp.model.entity;

import com.ddicg.erp.model.base.BaseEntity;
import com.ddicg.erp.model.embedded.SkuInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Category")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Category extends BaseEntity<Long> {

        @Column(name = "name")
        String name;

        /**
         * Thông tin SKU
         * 
         * @en SKU info
         */
        @Embedded
        @AttributeOverrides(@AttributeOverride(name = "sku", column = @Column(name = "sku_name")))
        SkuInfo skuInfo;

        /**
         * Danh sách sản phẩm
         * 
         * @en Products list
         */
        @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
        @JsonIgnore
        @Builder.Default
        List<Product> products = new ArrayList<>();
}
