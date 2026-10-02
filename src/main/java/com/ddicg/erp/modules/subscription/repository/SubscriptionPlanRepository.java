package com.ddicg.erp.modules.subscription.repository;

import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    Optional<SubscriptionPlan> findByPlanCode(String planCode);
    Optional<SubscriptionPlan> findByGoogleProductIdAndBasePlanId(String googleProductId, String basePlanId);
}
