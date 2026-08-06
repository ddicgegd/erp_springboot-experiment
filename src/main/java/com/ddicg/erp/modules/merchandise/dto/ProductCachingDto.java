package com.ddicg.erp.modules.merchandise.dto;

import lombok.*;

import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProductCachingDto {
    private String recommendationId;
    private String strategy;
    private long generatedAt;
    private List<ProductDto> items;

    public static ProductCachingDtoBuilder builder() { return new ProductCachingDtoBuilder(); }
    public static class ProductCachingDtoBuilder {
        private String id;
        private Object product;
        ProductCachingDtoBuilder() {}
        public ProductCachingDtoBuilder id(String id) { this.id = id; return this; }
        public ProductCachingDtoBuilder strategy(String strategy) { return this; }
        public ProductCachingDtoBuilder product(Object product) { this.product = product; return this; }
        public ProductCachingDtoBuilder recommendationId(String recommendationId) { return this; }
        public ProductCachingDtoBuilder status(Object status) { return this; }
        public ProductCachingDto build() { ProductCachingDto p = new ProductCachingDto(); return p; }
    }

}
