package com.ddicg.erp.modules.merchandise.dto.request;

import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.ddicg.erp.modules.merchandise.dto.PromotionDto;
import com.ddicg.erp.modules.merchandise.dto.SpecificationGroupDto;
import com.ddicg.erp.modules.merchandise.dto.VariantOptionDto;
import java.util.List;
import java.util.Set;

public class UpdateAttributesRequest {
    private String id;
    private String name;
    private Double price;
    private Double salePrice;
    private List<VariantOptionDto> variantOptions;
    private StockStatus status;
    private Set<String> keywords;
    private List<SpecificationGroupDto> specifications;
    private List<PromotionDto> promotions;

    public UpdateAttributesRequest() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Double getSalePrice() { return salePrice; }
    public void setSalePrice(Double salePrice) { this.salePrice = salePrice; }
    public List<VariantOptionDto> getVariantOptions() { return variantOptions; }
    public void setVariantOptions(List<VariantOptionDto> variantOptions) { this.variantOptions = variantOptions; }
    public StockStatus getStatus() { return status; }
    public void setStatus(StockStatus status) { this.status = status; }
    public Set<String> getKeywords() { return keywords; }
    public void setKeywords(Set<String> keywords) { this.keywords = keywords; }
    public List<SpecificationGroupDto> getSpecifications() { return specifications; }
    public void setSpecifications(List<SpecificationGroupDto> specifications) { this.specifications = specifications; }
    public List<PromotionDto> getPromotions() { return promotions; }
    public void setPromotions(List<PromotionDto> promotions) { this.promotions = promotions; }
}
