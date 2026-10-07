package com.ddicg.erp.modules.subscription.repository;

import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    Optional<UserSubscription> findByUserIdAndSubscriptionStateIn(Long userId, List<SubscriptionState> states);
    Optional<UserSubscription> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<UserSubscription> findByPaymentProfileId(String paymentProfileId);
    List<UserSubscription> findBySubscriptionStateAndExpiryTimeBefore(SubscriptionState state, LocalDateTime time);
    List<UserSubscription> findBySubscriptionStateInAndExpiryTimeBefore(List<SubscriptionState> states, LocalDateTime time);
    List<UserSubscription> findByAutoRenewingTrueAndSubscriptionStateInAndExpiryTimeLessThanEqual(List<SubscriptionState> states, LocalDateTime time);
    List<UserSubscription> findBySubscriptionStateNotAndExpiryTimeLessThanEqual(SubscriptionState state, LocalDateTime time);
    boolean existsByUserIdAndSubscriptionStateInAndExpiryTimeAfter(Long userId, List<SubscriptionState> states, LocalDateTime time);
}
