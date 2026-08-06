package com.ddicg.erp.core.common.dto.response;

public class PageableData {
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;

    public PageableData() {}

    public PageableData(int pageNumber, int pageSize, long totalElements, int totalPages) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public static PageableDataBuilder builder() { return new PageableDataBuilder(); }

    public static class PageableDataBuilder {
        private int pageNumber;
        private int pageSize;
        private long totalElements;
        private int totalPages;

        PageableDataBuilder() {}

        public PageableDataBuilder pageNumber(int pageNumber) { this.pageNumber = pageNumber; return this; }
        public PageableDataBuilder pageSize(int pageSize) { this.pageSize = pageSize; return this; }
        public PageableDataBuilder totalElements(long totalElements) { this.totalElements = totalElements; return this; }
        public PageableDataBuilder totalPages(int totalPages) { this.totalPages = totalPages; return this; }

        public PageableData build() {
            return new PageableData(pageNumber, pageSize, totalElements, totalPages);
        }
    }

    public static PageableData from(org.springframework.data.domain.Page<?> page) {
        PageableData pd = new PageableData();
        pd.setPageNumber(page.getNumber());
        pd.setPageSize(page.getSize());
        pd.setTotalElements(page.getTotalElements());
        pd.setTotalPages(page.getTotalPages());
        return pd;
    }
}
