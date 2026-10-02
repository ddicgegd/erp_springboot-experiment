package com.ddicg.erp.modules.subscription.model;

import com.ddicg.erp.core.common.model.base.BaseEntity;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserSubscription extends BaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "user_id", nullable = false)
    Long userId;

    @Column(name = "plan_id", nullable = false)
    Long planId;

    @Column(name = "payment_profile_id", length = 64)
    String paymentProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_state", length = 30, nullable = false)
    SubscriptionState subscriptionState;

    @Column(name = "is_trial", nullable = false)
    @Builder.Default
    Boolean isTrial = false;

    @Column(name = "start_time", nullable = false)
    LocalDateTime startTime;

    @Column(name = "expiry_time", nullable = false)
    LocalDateTime expiryTime;

    @Column(name = "auto_renewing", nullable = false)
    @Builder.Default
    Boolean autoRenewing = true;

    @Column(name = "cancellation_reason", length = 500)
    String cancellationReason;

    @Column(name = "last_payment_time")
    LocalDateTime lastPaymentTime;

    @Column(name = "last_fineract_journal_id", length = 64)
    String lastFineractJournalId;
}
