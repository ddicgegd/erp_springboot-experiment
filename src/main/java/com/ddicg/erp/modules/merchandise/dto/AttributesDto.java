package com.ddicg.erp.modules.merchandise.dto;

import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.core.common.model.enums.StockStatus;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * DTO for {@link Attributes}
 */
public class AttributesDto implements Serializable {

    private Long id;
    private String name;
    private SkuInfoDto sku;
    private double price;
    private double salePrice;
    private List<VariantOptionDto> variantOptions;
    private StockStatus statusProduct;
    private List<SpecificationGroupDto> specifications;
    private List<PromotionDto> promotions;
    private Set<String> keywords;
    private AuditInfoDto auditInfo;
    private ProductDto product;

    public AttributesDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    // getSku() returns SkuInfoDto - used as dto.getSku().getSku() in service
    public SkuInfoDto getSku() { return sku; }
    public void setSku(SkuInfoDto sku) { this.sku = sku; }

    public SkuInfoDto getSkuInfo() { return sku; }
    public String getSkuStr() { return sku != null ? sku.getSku() : null; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getSalePrice() { return salePrice; }
    public void setSalePrice(double salePrice) { this.salePrice = salePrice; }

    public List<VariantOptionDto> getVariantOptions() { return variantOptions; }
    public void setVariantOptions(List<VariantOptionDto> variantOptions) { this.variantOptions = variantOptions; }

    public StockStatus getStatusProduct() { return statusProduct; }
    public void setStatusProduct(StockStatus statusProduct) { this.statusProduct = statusProduct; }

    public List<SpecificationGroupDto> getSpecifications() { return specifications; }
    public void setSpecifications(List<SpecificationGroupDto> specifications) { this.specifications = specifications; }

    public List<PromotionDto> getPromotions() { return promotions; }
    public void setPromotions(List<PromotionDto> promotions) { this.promotions = promotions; }

    public Set<String> getKeywords() { return keywords; }
    public void setKeywords(Set<String> keywords) { this.keywords = keywords; }

    public AuditInfoDto getAuditInfo() { return auditInfo; }
    public void setAuditInfo(AuditInfoDto auditInfo) { this.auditInfo = auditInfo; }

    public ProductDto getProduct() { return product; }
    public void setProduct(ProductDto product) { this.product = product; }
}
