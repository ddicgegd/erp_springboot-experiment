package com.ddicg.erp.core.common.model.base;

import com.ddicg.erp.core.config.converter.AuditEntryListConverter;
import com.ddicg.erp.core.common.model.embedded.AuditEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Base entity with audit fields + soft delete.
 * Các entity extend class này KHÔNG cần @Embedded AuditInfo.
 * 
 * @en Base entity with audit + soft delete.
 */
@MappedSuperclass
@Getter
@Setter
@ToString
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class BaseEntity<T extends Serializable> extends IdentityOnly<T> {

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "updated_by")
  String updatedBy;

  @Column(name = "created_at", updatable = false)
  LocalDateTime createdAt;

  @Column(name = "updated_at")
  LocalDateTime updatedAt;

  @Builder.Default
  @Column(name = "is_deleted")
  Boolean isDeleted = false;

  // ─── Soft delete fields (từ AuditInfo cũ) ───

  @Column(name = "deleted_at")
  LocalDateTime deletedAt;

  @Column(name = "deleted_by")
  String deletedBy;

  @Convert(converter = AuditEntryListConverter.class)
  @Column(name = "update_history", columnDefinition = "CLOB")
  @Builder.Default
  List<AuditEntry> updateHistory = new ArrayList<>();

  // ─── Helper methods (từ AuditInfo cũ) ───

  public void addUpdateEntry(String action, String updatedBy) {
    if (this.updateHistory == null) this.updateHistory = new ArrayList<>();
    this.updateHistory.add(AuditEntry.builder()
        .action(action)
        .updatedBy(updatedBy)
        .updatedAt(LocalDateTime.now())
        .build());
    this.updatedAt = LocalDateTime.now();
  }

  public void markDeletedAfter30Days(String deletedByUser) {
    this.deletedAt = LocalDateTime.now().plusDays(30);
    this.deletedBy = deletedByUser;
  }

  public void markDeletedNow(String deletedByUser) {
    this.deletedAt = LocalDateTime.now();
    this.deletedBy = deletedByUser;
  }

  public void restore() {
    this.deletedAt = null;
    this.deletedBy = null;
  }

  public boolean isSoftDeleted() {
    return deletedAt != null;
  }

    @jakarta.persistence.PrePersist
    public void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }

    @jakarta.persistence.PreUpdate
    public void onPreUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public T getId() { return super.getId(); }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

}
