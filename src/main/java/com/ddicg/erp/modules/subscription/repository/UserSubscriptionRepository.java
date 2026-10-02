package com.ddicg.erp.modules.subscription.repository;

import com.ddicg.erp.modules.subscription.model.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    Optional<UserSubscription> findByPurchaseToken(String purchaseToken);
    List<UserSubscription> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<UserSubscription> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
