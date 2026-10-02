package com.ddicg.erp.modules.subscription.model;

import com.ddicg.erp.core.common.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "subscription_audit_logs", indexes = {
        @Index(name = "idx_sub_audit_user", columnList = "user_id"),
        @Index(name = "idx_sub_audit_sub_id", columnList = "subscription_id")
})
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubscriptionAuditLog extends BaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "user_id")
    Long userId;

    @Column(name = "subscription_id")
    Long subscriptionId;

    @Column(name = "event_type", length = 50, nullable = false)
    String eventType;

    @Column(name = "previous_state", length = 30)
    String previousState;

    @Column(name = "new_state", length = 30)
    String newState;

    @Column(name = "amount", precision = 15, scale = 2)
    BigDecimal amount;

    @Column(name = "currency", length = 10)
    String currency;

    @Column(name = "fineract_journal_id", length = 64)
    String fineractJournalId;

    @Lob
    @Column(name = "payload")
    String payload;
}
