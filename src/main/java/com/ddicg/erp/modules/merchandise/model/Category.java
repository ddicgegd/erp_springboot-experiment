package com.ddicg.erp.modules.merchandise.model;

import com.ddicg.erp.core.common.model.base.BaseEntity;
import com.ddicg.erp.core.common.model.embedded.SkuInfo;
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

        @Embedded
        @AttributeOverrides(@AttributeOverride(name = "sku", column = @Column(name = "sku_name")))
        SkuInfo skuInfo;

        @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
        @JsonIgnore
        @Builder.Default
        List<Product> products = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public SkuInfo getSkuInfo() { return skuInfo; }
        public void setSkuInfo(SkuInfo skuInfo) { this.skuInfo = skuInfo; }
        public List<Product> getProducts() { return products; }
        public void setProducts(List<Product> products) { this.products = products; }
}
