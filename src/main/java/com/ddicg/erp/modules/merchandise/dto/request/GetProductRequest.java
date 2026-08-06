package com.ddicg.erp.modules.merchandise.dto.request;

import com.ddicg.erp.core.common.dto.request.PagingRequest;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.LocalDateTime;
import java.util.List;

public class GetProductRequest {
    private String keyword;
    private String categoryId;
    @JsonAlias({ "category_sku", "categorySku" })
    private String categorySku;
    private String createdBy;
    private List<String> productIds;
    private List<String> skus;
    private List<String> statuses;
    private List<String> categoryIds;
    @JsonAlias({ "category_skus", "categorySkus" })
    private List<String> categorySkus;
    private Integer minSoldQuantity;
    private Integer maxSoldQuantity;
    private Double minRevenue;
    private Double maxRevenue;
    private Integer minOrders;
    private Integer maxOrders;
    private Integer minView;
    private Double minRating;
    private Integer minReviews;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
    private LocalDateTime updatedFrom;
    private LocalDateTime updatedTo;
    private PagingRequest paging = new PagingRequest();

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getCategorySku() { return categorySku; }
    public void setCategorySku(String categorySku) { this.categorySku = categorySku; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public List<String> getProductIds() { return productIds; }
    public void setProductIds(List<String> productIds) { this.productIds = productIds; }
    public List<String> getSkus() { return skus; }
    public void setSkus(List<String> skus) { this.skus = skus; }
    public List<String> getStatuses() { return statuses; }
    public void setStatuses(List<String> statuses) { this.statuses = statuses; }
    public List<String> getCategoryIds() { return categoryIds; }
    public void setCategoryIds(List<String> categoryIds) { this.categoryIds = categoryIds; }
    public List<String> getCategorySkus() { return categorySkus; }
    public void setCategorySkus(List<String> categorySkus) { this.categorySkus = categorySkus; }
    public Integer getMinSoldQuantity() { return minSoldQuantity; }
    public void setMinSoldQuantity(Integer minSoldQuantity) { this.minSoldQuantity = minSoldQuantity; }
    public Integer getMaxSoldQuantity() { return maxSoldQuantity; }
    public void setMaxSoldQuantity(Integer maxSoldQuantity) { this.maxSoldQuantity = maxSoldQuantity; }
    public Double getMinRevenue() { return minRevenue; }
    public void setMinRevenue(Double minRevenue) { this.minRevenue = minRevenue; }
    public Double getMaxRevenue() { return maxRevenue; }
    public void setMaxRevenue(Double maxRevenue) { this.maxRevenue = maxRevenue; }
    public Integer getMinOrders() { return minOrders; }
    public void setMinOrders(Integer minOrders) { this.minOrders = minOrders; }
    public Integer getMaxOrders() { return maxOrders; }
    public void setMaxOrders(Integer maxOrders) { this.maxOrders = maxOrders; }
    public Integer getMinView() { return minView; }
    public void setMinView(Integer minView) { this.minView = minView; }
    public Double getMinRating() { return minRating; }
    public void setMinRating(Double minRating) { this.minRating = minRating; }
    public Integer getMinReviews() { return minReviews; }
    public void setMinReviews(Integer minReviews) { this.minReviews = minReviews; }
    public LocalDateTime getCreatedFrom() { return createdFrom; }
    public void setCreatedFrom(LocalDateTime createdFrom) { this.createdFrom = createdFrom; }
    public LocalDateTime getCreatedTo() { return createdTo; }
    public void setCreatedTo(LocalDateTime createdTo) { this.createdTo = createdTo; }
    public LocalDateTime getUpdatedFrom() { return updatedFrom; }
    public void setUpdatedFrom(LocalDateTime updatedFrom) { this.updatedFrom = updatedFrom; }
    public LocalDateTime getUpdatedTo() { return updatedTo; }
    public void setUpdatedTo(LocalDateTime updatedTo) { this.updatedTo = updatedTo; }
    public PagingRequest getPaging() { return paging; }
    public void setPaging(PagingRequest paging) { this.paging = paging; }
}
