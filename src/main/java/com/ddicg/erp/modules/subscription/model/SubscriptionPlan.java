package com.ddicg.erp.modules.subscription.model;

import com.ddicg.erp.core.common.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubscriptionPlan extends BaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "plan_code", length = 50, nullable = false, unique = true)
    String planCode;

    @Column(name = "name", length = 100, nullable = false)
    String name;

    @Column(name = "target_rank", length = 30, nullable = false)
    String targetRank;

    @Column(name = "billing_period_months", nullable = false)
    Integer billingPeriodMonths;

    @Column(name = "free_trial_days", nullable = false)
    @Builder.Default
    Integer freeTrialDays = 30;

    @Column(name = "price_amount", precision = 15, scale = 2, nullable = false)
    BigDecimal priceAmount;

    @Column(name = "currency", length = 10, nullable = false)
    @Builder.Default
    String currency = "VND";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    Boolean isActive = true;

    @Column(name = "description", length = 500)
    String description;
}
