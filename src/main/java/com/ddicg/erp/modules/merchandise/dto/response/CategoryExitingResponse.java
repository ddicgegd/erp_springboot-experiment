package com.ddicg.erp.modules.merchandise.dto.response;

public class CategoryExitingResponse {
    private String id;
    private boolean isExiting;

    public CategoryExitingResponse() {}

    public CategoryExitingResponse(String id, boolean isExiting) {
        this.id = id;
        this.isExiting = isExiting;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public boolean isExiting() { return isExiting; }
    public void setExiting(boolean isExiting) { this.isExiting = isExiting; }

    public static CategoryExitingResponseBuilder builder() {
        return new CategoryExitingResponseBuilder();
    }

    public static class CategoryExitingResponseBuilder {
        private String id;
        private boolean isExiting;

        CategoryExitingResponseBuilder() {}

        public CategoryExitingResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        public CategoryExitingResponseBuilder isExiting(boolean isExiting) {
            this.isExiting = isExiting;
            return this;
        }

        public CategoryExitingResponse build() {
            return new CategoryExitingResponse(this.id, this.isExiting);
        }
    }
}
