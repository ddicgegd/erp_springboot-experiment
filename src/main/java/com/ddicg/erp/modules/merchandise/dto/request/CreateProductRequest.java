package com.ddicg.erp.modules.merchandise.dto.request;

import java.time.LocalDateTime;

public class CreateProductRequest {
    private String name;
    private String categorySku;
    private String status;
    private Double discountPercent;
    private LocalDateTime discountStartDate;
    private LocalDateTime discountEndDate;

    public CreateProductRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategorySku() { return categorySku; }
    public void setCategorySku(String categorySku) { this.categorySku = categorySku; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Double discountPercent) { this.discountPercent = discountPercent; }
    public LocalDateTime getDiscountStartDate() { return discountStartDate; }
    public void setDiscountStartDate(LocalDateTime discountStartDate) { this.discountStartDate = discountStartDate; }
    public LocalDateTime getDiscountEndDate() { return discountEndDate; }
    public void setDiscountEndDate(LocalDateTime discountEndDate) { this.discountEndDate = discountEndDate; }
}
