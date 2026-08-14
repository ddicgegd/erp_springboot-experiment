package com.ddicg.erp.modules.merchandise.dto.request;

import jakarta.validation.constraints.NotBlank;

public class UpdateCategoryRequest {
    @NotBlank
    private String sku;
    private String name;
    private String description;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
