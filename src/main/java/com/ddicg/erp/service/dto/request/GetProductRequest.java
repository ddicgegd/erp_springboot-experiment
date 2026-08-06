package com.ddicg.erp.service.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
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
}
