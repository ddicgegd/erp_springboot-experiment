package com.ddicg.erp.modules.merchandise.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ActiveLogDto implements Serializable {
    private String performedBy;
    private String status;
    private String targetID;
    private String description;
    private LocalDateTime createdAt;

    public ActiveLogDto() {}

    public ActiveLogDto(String performedBy, String status, String targetID, String description, LocalDateTime createdAt) {
        this.performedBy = performedBy;
        this.status = status;
        this.targetID = targetID;
        this.description = description;
        this.createdAt = createdAt;
    }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTargetID() { return targetID; }
    public void setTargetID(String targetID) { this.targetID = targetID; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static ActiveLogDtoBuilder builder() { return new ActiveLogDtoBuilder(); }

    public static class ActiveLogDtoBuilder {
        private String performedBy;
        private String status;
        private String targetID;
        private String description;
        private LocalDateTime createdAt;

        ActiveLogDtoBuilder() {}

        public ActiveLogDtoBuilder performedBy(String performedBy) { this.performedBy = performedBy; return this; }
        public ActiveLogDtoBuilder status(String status) { this.status = status; return this; }
        public ActiveLogDtoBuilder targetID(String targetID) { this.targetID = targetID; return this; }
        public ActiveLogDtoBuilder description(String description) { this.description = description; return this; }
        public ActiveLogDtoBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public ActiveLogDto build() {
            return new ActiveLogDto(performedBy, status, targetID, description, createdAt);
        }
    }
}
