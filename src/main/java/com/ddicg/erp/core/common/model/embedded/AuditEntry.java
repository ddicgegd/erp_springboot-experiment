package com.ddicg.erp.core.common.model.embedded;

import java.time.LocalDateTime;

public class AuditEntry {
    private String action;
    private String performBy;
    private String updatedBy;
    private LocalDateTime timestamp;
    private LocalDateTime updatedAt;
    private String details;

    public AuditEntry() {}

    public AuditEntry(String action, String performBy, String updatedBy, LocalDateTime timestamp, LocalDateTime updatedAt, String details) {
        this.action = action;
        this.performBy = performBy;
        this.updatedBy = updatedBy;
        this.timestamp = timestamp;
        this.updatedAt = updatedAt;
        this.details = details;
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getPerformBy() { return performBy; }
    public void setPerformBy(String performBy) { this.performBy = performBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public static AuditEntryBuilder builder() {
        return new AuditEntryBuilder();
    }

    public static class AuditEntryBuilder {
        private String action;
        private String performBy;
        private String updatedBy;
        private LocalDateTime timestamp;
        private LocalDateTime updatedAt;
        private String details;

        AuditEntryBuilder() {}

        public AuditEntryBuilder action(String action) { this.action = action; return this; }
        public AuditEntryBuilder performBy(String performBy) { this.performBy = performBy; return this; }
        public AuditEntryBuilder updatedBy(String updatedBy) { this.updatedBy = updatedBy; return this; }
        public AuditEntryBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public AuditEntryBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public AuditEntryBuilder details(String details) { this.details = details; return this; }

        public AuditEntry build() {
            return new AuditEntry(this.action, this.performBy, this.updatedBy, this.timestamp, this.updatedAt, this.details);
        }
    }
}
