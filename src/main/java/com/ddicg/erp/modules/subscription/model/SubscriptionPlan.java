package com.ddicg.erp.modules.subscription.model;

import com.ddicg.erp.core.common.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubscriptionPlan extends BaseEntity<Long> {

    @Column(name = "plan_code", unique = true, nullable = false, length = 50)
    String planCode;

    @Column(name = "google_product_id", nullable = false, length = 100)
    String googleProductId;

    @Column(name = "base_plan_id", nullable = false, length = 50)
    String basePlanId;

    @Column(name = "target_rank", nullable = false, length = 20)
    String targetRank;

    @Column(name = "billing_period_months", nullable = false)
    Integer billingPeriodMonths;

    @Column(name = "free_trial_days", nullable = false)
    @Builder.Default
    Integer freeTrialDays = 30;

    @Column(name = "price_amount", nullable = false, precision = 15, scale = 2)
    BigDecimal priceAmount;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    String currency = "VND";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    Boolean isActive = true;
}
