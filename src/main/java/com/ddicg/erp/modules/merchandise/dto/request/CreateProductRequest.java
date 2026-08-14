package com.ddicg.erp.modules.merchandise.dto.request;

public class CreateProductRequest {
    private String name;
    private String categorySku;
    private String status;

    public CreateProductRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategorySku() { return categorySku; }
    public void setCategorySku(String categorySku) { this.categorySku = categorySku; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
