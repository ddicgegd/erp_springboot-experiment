package com.ddicg.erp.core.common.model.embedded;

import jakarta.persistence.Embeddable;
import org.jspecify.annotations.NonNull;
import java.util.concurrent.ThreadLocalRandom;

@Embeddable
public class SkuInfo {
    private String sku;

    public SkuInfo() {}

    public SkuInfo(String sku) {
        this.sku = sku;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public SkuInfo createSku(@NonNull String name) {
        return new SkuInfo(this.sku = name.toLowerCase() + (ThreadLocalRandom.current().nextInt(1000, 10000)));
    }

    public static SkuInfoBuilder builder() { return new SkuInfoBuilder(); }
    public static class SkuInfoBuilder {
        private String sku;
        private String barcode;
        SkuInfoBuilder() {}
        public SkuInfoBuilder sku(String sku) { this.sku = sku; return this; }
        public SkuInfoBuilder barcode(String barcode) { this.barcode = barcode; return this; }
        public SkuInfo build() { SkuInfo s = new SkuInfo(); return s; }
    }

}
