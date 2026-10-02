package com.ddicg.erp.modules.subscription.model;

import com.ddicg.erp.core.common.model.base.BaseEntity;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_subscriptions", indexes = {
        @Index(name = "idx_user_sub_user_id", columnList = "user_id"),
        @Index(name = "idx_user_sub_token", columnList = "purchase_token"),
        @Index(name = "idx_user_sub_state", columnList = "subscription_state")
})
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserSubscription extends BaseEntity<Long> {

    @Column(name = "user_id", nullable = false)
    Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    User user;

    @Column(name = "plan_id", nullable = false)
    Long planId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", insertable = false, updatable = false)
    SubscriptionPlan plan;

    @Column(name = "purchase_token", nullable = false, unique = true, length = 500)
    String purchaseToken;

    @Column(name = "order_id", length = 100)
    String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_state", nullable = false, length = 30)
    SubscriptionState subscriptionState;

    @Column(name = "is_trial", nullable = false)
    @Builder.Default
    Boolean isTrial = true;

    @Column(name = "start_time", nullable = false)
    LocalDateTime startTime;

    @Column(name = "expiry_time", nullable = false)
    LocalDateTime expiryTime;

    @Column(name = "auto_renewing", nullable = false)
    @Builder.Default
    Boolean autoRenewing = true;

    @Column(name = "linked_purchase_token", length = 500)
    String linkedPurchaseToken;
}
