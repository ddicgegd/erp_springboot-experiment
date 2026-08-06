package com.ddicg.erp.service.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import java.util.List;

import java.time.LocalDateTime;

@Data
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
}
