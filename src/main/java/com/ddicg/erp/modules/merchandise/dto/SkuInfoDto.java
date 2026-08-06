package com.ddicg.erp.modules.merchandise.dto;

import java.io.Serializable;

public class SkuInfoDto implements Serializable {
    private String sku;

    public SkuInfoDto() {}

    public SkuInfoDto(String sku) {
        this.sku = sku;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
}
