package com.ddicg.erp.modules.merchandise.dto.request;

import java.util.List;
import java.util.Set;

public class CreateAttributesRequest {
    private String name;
    private String productSku;
    private Set<String> keywords;
    private List<AttributeInput> attributes;

    public CreateAttributesRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProductSku() { return productSku; }
    public void setProductSku(String productSku) { this.productSku = productSku; }
    public Set<String> getKeywords() { return keywords; }
    public void setKeywords(Set<String> keywords) { this.keywords = keywords; }
    public List<AttributeInput> getAttributes() { return attributes; }
    public void setAttributes(List<AttributeInput> attributes) { this.attributes = attributes; }
}
