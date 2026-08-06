package com.ddicg.erp.modules.merchandise.dto.request;

import com.ddicg.erp.core.common.dto.request.PagingRequest;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.LocalDateTime;
import java.util.List;

public class AttributesSearchRequest {
    private String keyword;
    private String productId;
    @JsonAlias({ "product_sku", "productSku" })
    private String productSku;
    private List<String> ids;
    private List<String> productIds;
    @JsonAlias({ "product_skus", "productSkus" })
    private List<String> productSkus;
    private List<String> skus;
    private List<String> statuses;
    private Double minPrice;
    private Double maxPrice;
    private Double minSalePrice;
    private Double maxSalePrice;
    private Integer minSoldQuantity;
    private Integer maxSoldQuantity;
    private Double minCostPrice;
    private Double maxCostPrice;
    private String createdBy;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
    private LocalDateTime updatedFrom;
    private LocalDateTime updatedTo;
    private PagingRequest paging = new PagingRequest();

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getProductSku() { return productSku; }
    public void setProductSku(String productSku) { this.productSku = productSku; }
    public List<String> getIds() { return ids; }
    public void setIds(List<String> ids) { this.ids = ids; }
    public List<String> getProductIds() { return productIds; }
    public void setProductIds(List<String> productIds) { this.productIds = productIds; }
    public List<String> getProductSkus() { return productSkus; }
    public void setProductSkus(List<String> productSkus) { this.productSkus = productSkus; }
    public List<String> getSkus() { return skus; }
    public void setSkus(List<String> skus) { this.skus = skus; }
    public List<String> getStatuses() { return statuses; }
    public void setStatuses(List<String> statuses) { this.statuses = statuses; }
    public Double getMinPrice() { return minPrice; }
    public void setMinPrice(Double minPrice) { this.minPrice = minPrice; }
    public Double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Double maxPrice) { this.maxPrice = maxPrice; }
    public Double getMinSalePrice() { return minSalePrice; }
    public void setMinSalePrice(Double minSalePrice) { this.minSalePrice = minSalePrice; }
    public Double getMaxSalePrice() { return maxSalePrice; }
    public void setMaxSalePrice(Double maxSalePrice) { this.maxSalePrice = maxSalePrice; }
    public Integer getMinSoldQuantity() { return minSoldQuantity; }
    public void setMinSoldQuantity(Integer minSoldQuantity) { this.minSoldQuantity = minSoldQuantity; }
    public Integer getMaxSoldQuantity() { return maxSoldQuantity; }
    public void setMaxSoldQuantity(Integer maxSoldQuantity) { this.maxSoldQuantity = maxSoldQuantity; }
    public Double getMinCostPrice() { return minCostPrice; }
    public void setMinCostPrice(Double minCostPrice) { this.minCostPrice = minCostPrice; }
    public Double getMaxCostPrice() { return maxCostPrice; }
    public void setMaxCostPrice(Double maxCostPrice) { this.maxCostPrice = maxCostPrice; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
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
