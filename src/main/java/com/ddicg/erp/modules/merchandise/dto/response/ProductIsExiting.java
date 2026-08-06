package com.ddicg.erp.modules.merchandise.dto.response;

public class ProductIsExiting {
    private String id;
    private boolean isExiting;
    private Object product;

    public ProductIsExiting() {}
    public ProductIsExiting(String id, boolean isExiting, Object product) {
        this.id = id;
        this.isExiting = isExiting;
        this.product = product;
    }

    public String getId() { return id; }
    public boolean isExiting() { return isExiting; }
    public Object getProduct() { return product; }

    public static ProductIsExitingBuilder builder() { return new ProductIsExitingBuilder(); }

    public static class ProductIsExitingBuilder {
        private String id;
        private boolean isExiting;
        private Object product;

        ProductIsExitingBuilder() {}

        public ProductIsExitingBuilder id(String id) { this.id = id; return this; }
        public ProductIsExitingBuilder isExiting(boolean isExiting) { this.isExiting = isExiting; return this; }
        public ProductIsExitingBuilder product(Object product) { this.product = product; return this; }

        public ProductIsExiting build() {
            return new ProductIsExiting(id, isExiting, product);
        }
    }
}
