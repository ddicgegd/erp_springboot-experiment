package com.ddicg.erp.modules.subscription.repository;

import com.ddicg.erp.modules.subscription.model.SubscriptionAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionAuditLogRepository extends JpaRepository<SubscriptionAuditLog, Long> {
    List<SubscriptionAuditLog> findBySubscriptionId(Long subscriptionId);
    List<SubscriptionAuditLog> findByPurchaseToken(String purchaseToken);
}
