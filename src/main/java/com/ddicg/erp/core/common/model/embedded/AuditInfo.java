package com.ddicg.erp.core.common.model.embedded;

import com.ddicg.erp.core.config.converter.AuditEntryListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EntityListeners;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Thông tin kiểm toán — dùng cho entity extend IdentityOnly (Order, ShoppingCart).
 * 
 * Entity extend BaseEntity đã có sẵn createdAt/createdBy/updatedAt/updatedBy/isDeleted
 * nên KHÔNG cần @Embedded AuditInfo — chỉ cần tự thêm deletedAt/deletedBy/updateHistory.
 * 
 * @en Audit info — for IdentityOnly entities (Order, ShoppingCart).
 *     BaseEntity entities already have createdAt/createdBy/updatedAt/updatedBy/isDeleted.
 */
@Embeddable
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuditInfo {

    @CreatedDate
    @Column(name = "created_at", insertable = false, updatable = false)
    LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", insertable = false, updatable = false)
    String createdBy;

    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @Convert(converter = AuditEntryListConverter.class)
    @Column(name = "update_history", columnDefinition = "CLOB")
    @Builder.Default
    List<AuditEntry> updateHistory = new ArrayList<>();

    @Column(name = "deleted_at")
    LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    String deletedBy;

    public void addUpdateEntry(String action, String updatedBy) {
        if (this.updateHistory == null) this.updateHistory = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        this.updateHistory.add(AuditEntry.builder()
                .action(action)
                .updatedBy(updatedBy)
                .updatedAt(now)
                .build());
        this.updatedAt = now;
    }

    public AuditEntry getLatestUpdate() {
        if (updateHistory == null || updateHistory.isEmpty()) return null;
        return updateHistory.getLast();
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

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

}
