package com.ddicg.erp.modules.subscription.model;

import com.ddicg.erp.core.common.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_audit_logs", indexes = {
        @Index(name = "idx_sub_audit_sub_id", columnList = "subscription_id"),
        @Index(name = "idx_sub_audit_token", columnList = "purchase_token")
})
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubscriptionAuditLog extends BaseEntity<Long> {

    @Column(name = "subscription_id")
    Long subscriptionId;

    @Column(name = "purchase_token", length = 500)
    String purchaseToken;

    @Column(name = "notification_type", nullable = false)
    Integer notificationType;

    @Lob
    @Column(name = "raw_payload")
    String rawPayload;

    @Column(name = "processed_at", nullable = false)
    @Builder.Default
    LocalDateTime processedAt = LocalDateTime.now();
}
