package com.ddicg.erp.modules.merchandise.dto;

import com.ddicg.erp.modules.merchandise.model.Category;
import java.io.Serializable;

public class CategoryDto implements Serializable {

    private Long id;
    private String name;
    private SkuInfoDto skuInfo;
    private Long productCount;

    public CategoryDto() {}

    public CategoryDto(Long id, String name, SkuInfoDto skuInfo, Long productCount) {
        this.id = id;
        this.name = name;
        this.skuInfo = skuInfo;
        this.productCount = productCount;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public SkuInfoDto getSkuInfo() { return skuInfo; }
    public void setSkuInfo(SkuInfoDto skuInfo) { this.skuInfo = skuInfo; }
    public Long getProductCount() { return productCount; }
    public void setProductCount(Long productCount) { this.productCount = productCount; }
}
