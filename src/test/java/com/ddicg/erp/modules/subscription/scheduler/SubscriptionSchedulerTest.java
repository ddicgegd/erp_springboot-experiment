package com.ddicg.erp.modules.subscription.scheduler;

import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import com.ddicg.erp.modules.subscription.service.SubscriptionBillingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionSchedulerTest {

    @Mock
    private SubscriptionBillingService subscriptionBillingService;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SubscriptionScheduler scheduler;

    @Test
    @DisplayName("scanAndRenewSubscriptions: Tự động gia hạn các subscription đến hạn")
    void testScanAndRenewSubscriptions_DueFound() {
        UserSubscription sub1 = UserSubscription.builder()
                .id(101L)
                .userId(1L)
                .autoRenewing(true)
                .subscriptionState(SubscriptionState.ACTIVE)
                .expiryTime(LocalDateTime.now().minusMinutes(5))
                .build();

        UserSubscription sub2 = UserSubscription.builder()
                .id(102L)
                .userId(2L)
                .autoRenewing(true)
                .subscriptionState(SubscriptionState.TRIALING)
                .expiryTime(LocalDateTime.now().minusMinutes(10))
                .build();

        when(userSubscriptionRepository.findByAutoRenewingTrueAndSubscriptionStateInAndExpiryTimeLessThanEqual(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(sub1, sub2));
        when(subscriptionBillingService.processRecurringRenewal(101L)).thenReturn(true);
        when(subscriptionBillingService.processRecurringRenewal(102L)).thenReturn(false);

        scheduler.scanAndRenewSubscriptions();

        verify(subscriptionBillingService, times(1)).processRecurringRenewal(101L);
        verify(subscriptionBillingService, times(1)).processRecurringRenewal(102L);
    }

    @Test
    @DisplayName("scanAndRenewSubscriptions: Xử lý ngoại lệ mượt mà, lỗi ở sub1 không ngắt tiến trình xử lý sub2")
    void testScanAndRenewSubscriptions_ExceptionResilience() {
        UserSubscription sub1 = UserSubscription.builder()
                .id(101L)
                .userId(1L)
                .autoRenewing(true)
                .subscriptionState(SubscriptionState.ACTIVE)
                .expiryTime(LocalDateTime.now().minusMinutes(5))
                .build();

        UserSubscription sub2 = UserSubscription.builder()
                .id(102L)
                .userId(2L)
                .autoRenewing(true)
                .subscriptionState(SubscriptionState.ACTIVE)
                .expiryTime(LocalDateTime.now().minusMinutes(10))
                .build();

        when(userSubscriptionRepository.findByAutoRenewingTrueAndSubscriptionStateInAndExpiryTimeLessThanEqual(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(sub1, sub2));
        when(subscriptionBillingService.processRecurringRenewal(101L)).thenThrow(new RuntimeException("Gateway Timeout"));
        when(subscriptionBillingService.processRecurringRenewal(102L)).thenReturn(true);

        scheduler.scanAndRenewSubscriptions();

        verify(subscriptionBillingService, times(1)).processRecurringRenewal(101L);
        verify(subscriptionBillingService, times(1)).processRecurringRenewal(102L);
    }

    @Test
    @DisplayName("scanAndRenewSubscriptions: Không có subscription nào đến hạn -> Không xử lý gì")
    void testScanAndRenewSubscriptions_Empty() {
        when(userSubscriptionRepository.findByAutoRenewingTrueAndSubscriptionStateInAndExpiryTimeLessThanEqual(anyList(), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        scheduler.scanAndRenewSubscriptions();

        verifyNoInteractions(subscriptionBillingService);
    }

    @Test
    @DisplayName("scanAndExpireOverdueSubscriptions: Chuyển sang EXPIRED và hạ rank về MEMBER khi không còn subscription active")
    void testScanAndExpireOverdueSubscriptions_ExpireAndDowngrade() {
        UserSubscription expiredSub = UserSubscription.builder()
                .id(201L)
                .userId(5L)
                .autoRenewing(false)
                .subscriptionState(SubscriptionState.ACTIVE)
                .expiryTime(LocalDateTime.now().minusDays(1))
                .build();

        User user = User.builder()
                .name("vipUser")
                .rank(UserRank.GOLD)
                .build();
        user.setId(5L);

        when(userSubscriptionRepository.findBySubscriptionStateNotAndExpiryTimeLessThanEqual(eq(SubscriptionState.EXPIRED), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredSub));
        when(userSubscriptionRepository.existsByUserIdAndSubscriptionStateInAndExpiryTimeAfter(eq(5L), anyList(), any(LocalDateTime.class)))
                .thenReturn(false);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        scheduler.scanAndExpireOverdueSubscriptions();

        verify(userSubscriptionRepository, times(1)).save(argThat(s -> s.getSubscriptionState() == SubscriptionState.EXPIRED));
        verify(userRepository, times(1)).save(argThat(u -> u.getUserRank() == UserRank.MEMBER));
    }

    @Test
    @DisplayName("scanAndExpireOverdueSubscriptions: Chuyển sang EXPIRED nhưng KHÔNG hạ rank nếu user vẫn còn subscription active khác")
    void testScanAndExpireOverdueSubscriptions_ExpireWithoutDowngrade() {
        UserSubscription expiredSub = UserSubscription.builder()
                .id(202L)
                .userId(6L)
                .autoRenewing(false)
                .subscriptionState(SubscriptionState.ACTIVE)
                .expiryTime(LocalDateTime.now().minusDays(1))
                .build();

        when(userSubscriptionRepository.findBySubscriptionStateNotAndExpiryTimeLessThanEqual(eq(SubscriptionState.EXPIRED), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredSub));
        // User vẫn còn subscription khác đang active
        when(userSubscriptionRepository.existsByUserIdAndSubscriptionStateInAndExpiryTimeAfter(eq(6L), anyList(), any(LocalDateTime.class)))
                .thenReturn(true);

        scheduler.scanAndExpireOverdueSubscriptions();

        verify(userSubscriptionRepository, times(1)).save(argThat(s -> s.getSubscriptionState() == SubscriptionState.EXPIRED));
        verify(userRepository, never()).findById(anyLong());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("scanAndExpireOverdueSubscriptions: Đang trong thời gian ân hạn (1 ngày <= 3 ngày) -> KHÔNG chuyển EXPIRED và KHÔNG hạ rank")
    void testScanAndExpireOverdueSubscriptions_InGracePeriod_WithinDeadline_NotExpired() {
        UserSubscription graceSub = UserSubscription.builder()
                .id(301L)
                .userId(7L)
                .autoRenewing(true)
                .subscriptionState(SubscriptionState.IN_GRACE_PERIOD)
                .expiryTime(LocalDateTime.now().minusDays(1)) // Mới quá hạn 1 ngày
                .build();

        when(userSubscriptionRepository.findBySubscriptionStateNotAndExpiryTimeLessThanEqual(eq(SubscriptionState.EXPIRED), any(LocalDateTime.class)))
                .thenReturn(List.of(graceSub));

        scheduler.scanAndExpireOverdueSubscriptions();

        verify(userSubscriptionRepository, never()).save(any());
        verify(userRepository, never()).findById(anyLong());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("scanAndExpireOverdueSubscriptions: Quá hạn ân hạn (4 ngày > 3 ngày) -> Chuyển EXPIRED và hạ rank về MEMBER")
    void testScanAndExpireOverdueSubscriptions_InGracePeriod_PastDeadline_ExpiresAndDowngrades() {
        UserSubscription graceSub = UserSubscription.builder()
                .id(302L)
                .userId(8L)
                .autoRenewing(true)
                .subscriptionState(SubscriptionState.IN_GRACE_PERIOD)
                .expiryTime(LocalDateTime.now().minusDays(4)) // Đã quá 3 ngày ân hạn
                .build();

        User user = User.builder()
                .name("goldUser")
                .rank(UserRank.GOLD)
                .build();
        user.setId(8L);

        when(userSubscriptionRepository.findBySubscriptionStateNotAndExpiryTimeLessThanEqual(eq(SubscriptionState.EXPIRED), any(LocalDateTime.class)))
                .thenReturn(List.of(graceSub));
        when(userSubscriptionRepository.existsByUserIdAndSubscriptionStateInAndExpiryTimeAfter(eq(8L), anyList(), any(LocalDateTime.class)))
                .thenReturn(false);
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));

        scheduler.scanAndExpireOverdueSubscriptions();

        verify(userSubscriptionRepository, times(1)).save(argThat(s -> s.getSubscriptionState() == SubscriptionState.EXPIRED));
        verify(userRepository, times(1)).save(argThat(u -> u.getUserRank() == UserRank.MEMBER));
    }

    @Test
    @DisplayName("scanAndExpireOverdueSubscriptions: Gói đã CANCELLED hoặc PAUSED và đã hết hạn -> Chuyển sang EXPIRED và hạ rank")
    void testScanAndExpireOverdueSubscriptions_CancelledOrPaused_ExpiresAndDowngrades() {
        UserSubscription cancelledSub = UserSubscription.builder()
                .id(303L)
                .userId(9L)
                .autoRenewing(false)
                .subscriptionState(SubscriptionState.CANCELLED)
                .expiryTime(LocalDateTime.now().minusDays(1))
                .build();

        User user = User.builder()
                .name("cancelledUser")
                .rank(UserRank.PLATINUM)
                .build();
        user.setId(9L);

        when(userSubscriptionRepository.findBySubscriptionStateNotAndExpiryTimeLessThanEqual(eq(SubscriptionState.EXPIRED), any(LocalDateTime.class)))
                .thenReturn(List.of(cancelledSub));
        when(userSubscriptionRepository.existsByUserIdAndSubscriptionStateInAndExpiryTimeAfter(eq(9L), anyList(), any(LocalDateTime.class)))
                .thenReturn(false);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));

        scheduler.scanAndExpireOverdueSubscriptions();

        verify(userSubscriptionRepository, times(1)).save(argThat(s -> s.getSubscriptionState() == SubscriptionState.EXPIRED));
        verify(userRepository, times(1)).save(argThat(u -> u.getUserRank() == UserRank.MEMBER));
    }

    @Test
    @DisplayName("checkAndDowngradeUserRank: User vốn dĩ đã là MEMBER -> Không gọi save user dư thừa")
    void testCheckAndDowngradeUserRank_UserAlreadyMember_NoRedundantSave() {
        User user = User.builder()
                .name("memberUser")
                .rank(UserRank.MEMBER)
                .build();
        user.setId(10L);

        when(userSubscriptionRepository.existsByUserIdAndSubscriptionStateInAndExpiryTimeAfter(eq(10L), anyList(), any(LocalDateTime.class)))
                .thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        scheduler.checkAndDowngradeUserRank(10L);

        verify(userRepository, never()).save(any());
    }
}
