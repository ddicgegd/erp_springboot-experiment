package com.ddicg.erp.core.common.dto.response;

import java.util.List;

public class PagingResponse<T> {
    private List<T> contents;
    private PageableData pageable;

    public PagingResponse() {}

    public PagingResponse(List<T> contents, PageableData pageable) {
        this.contents = contents;
        this.pageable = pageable;
    }

    public List<T> getContents() { return contents; }
    public void setContents(List<T> contents) { this.contents = contents; }
    public PageableData getPageable() { return pageable; }
    public void setPageable(PageableData pageable) { this.pageable = pageable; }

    public static <T> PagingResponseBuilder<T> builder() { return new PagingResponseBuilder<>(); }

    public static class PagingResponseBuilder<T> {
        private List<T> contents;
        private PageableData pageable;

        PagingResponseBuilder() {}

        public PagingResponseBuilder<T> contents(List<T> contents) { this.contents = contents; return this; }
        public PagingResponseBuilder<T> pageable(PageableData pageable) { this.pageable = pageable; return this; }
        public PagingResponseBuilder<T> paging(PageableData pageable) { this.pageable = pageable; return this; }

        public PagingResponse<T> build() {
            return new PagingResponse<>(contents, pageable);
        }
    }

    public static <T> PagingResponse<T> from(org.springframework.data.domain.Page<T> page) {
        PagingResponse<T> res = new PagingResponse<>();
        res.setContents(page.getContent());
        res.setPageable(PageableData.from(page));
        return res;
    }
}
