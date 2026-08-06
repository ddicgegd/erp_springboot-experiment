package com.ddicg.erp.modules.merchandise.dto.request;

import com.ddicg.erp.core.common.model.embedded.VariantOption;
import com.ddicg.erp.core.common.model.embedded.Promotion;
import com.ddicg.erp.core.common.model.embedded.SpecificationGroup;
import com.ddicg.erp.core.common.model.enums.StockStatus;
import java.math.BigDecimal;
import java.util.List;

public class AttributeInput {
    private String name;
    private String value;
    private BigDecimal price;
    private BigDecimal salePrice;
    private List<VariantOption> variantOptions;
    private List<Promotion> promotions;
    private List<SpecificationGroup> specifications;
    private StockStatus statusProduct;

    public AttributeInput() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public List<VariantOption> getVariantOptions() { return variantOptions; }
    public void setVariantOptions(List<VariantOption> variantOptions) { this.variantOptions = variantOptions; }
    public List<Promotion> getPromotions() { return promotions; }
    public void setPromotions(List<Promotion> promotions) { this.promotions = promotions; }
    public List<SpecificationGroup> getSpecifications() { return specifications; }
    public void setSpecifications(List<SpecificationGroup> specifications) { this.specifications = specifications; }
    public StockStatus getStatusProduct() { return statusProduct; }
    public void setStatusProduct(StockStatus statusProduct) { this.statusProduct = statusProduct; }
}
