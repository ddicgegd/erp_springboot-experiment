package com.ddicg.erp.modules.merchandise.dto;

import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import java.io.Serializable;
import java.util.List;

public class ProductDto implements Serializable {

    private Long id;
    private String name;
    private SkuInfoDto skuInfo;
    private List<MediaItemDto> mediaItems;
    private ActiveStatus status;
    private Integer viewCount;
    private Integer totalSoldQuantity;
    private java.math.BigDecimal totalRevenue;
    private String categoryName;

    public ProductDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public SkuInfoDto getSkuInfo() { return skuInfo; }
    public void setSkuInfo(SkuInfoDto skuInfo) { this.skuInfo = skuInfo; }

    public List<MediaItemDto> getMediaItems() { return mediaItems; }
    public void setMediaItems(List<MediaItemDto> mediaItems) { this.mediaItems = mediaItems; }

    public ActiveStatus getStatus() { return status; }
    public void setStatus(ActiveStatus status) { this.status = status; }

    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }

    public Integer getTotalSoldQuantity() { return totalSoldQuantity; }
    public void setTotalSoldQuantity(Integer totalSoldQuantity) { this.totalSoldQuantity = totalSoldQuantity; }

    public java.math.BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(java.math.BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
}
