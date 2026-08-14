package com.ddicg.erp.modules.merchandise.dto.request;

import com.ddicg.erp.core.common.dto.request.PagingRequest;
import java.time.LocalDateTime;
import java.util.List;

public class CategorySearchRequest {
    private List<String> skus;
    private List<String> names;

    private String keyword;
    private String createdBy;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
    private LocalDateTime updatedFrom;
    private LocalDateTime updatedTo;

    private PagingRequest paging = new PagingRequest();

    public List<String> getSkus() { return skus; }
    public void setSkus(List<String> skus) { this.skus = skus; }
    public List<String> getNames() { return names; }
    public void setNames(List<String> names) { this.names = names; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
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
    public void setPage(Integer page) { ensurePaging().setPage(page); }
    public void setSize(Integer size) { ensurePaging().setSize(size); }

    private PagingRequest ensurePaging() {
        if (paging == null) {
            paging = new PagingRequest();
        }
        return paging;
    }
}
