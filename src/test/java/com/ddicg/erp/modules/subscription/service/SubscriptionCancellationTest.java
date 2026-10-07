package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.SubscriptionAuditLog;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.SubscriptionAuditLogRepository;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import com.ddicg.erp.modules.subscription.scheduler.SubscriptionScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionCancellationTest {

    @Mock
    private SubscriptionPlanRepository planRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionAuditLogRepository auditLogRepository;

    @Mock
    private SubscriptionBillingService billingService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    @DisplayName("1. Người dùng hủy gia hạn: autoRenewing=false, state=CANCELLED, lưu lý do hủy, giữ nguyên expiryTime")
    void testCancelSubscription_Success() {
        Long userId = 1L;
        Long subId = 10L;
        LocalDateTime futureExpiry = LocalDateTime.now().plusDays(20);

        UserSubscription sub = UserSubscription.builder()
                .id(subId)
                .userId(userId)
                .planId(2L)
                .subscriptionState(SubscriptionState.ACTIVE)
                .autoRenewing(true)
                .startTime(LocalDateTime.now().minusDays(10))
                .expiryTime(futureExpiry)
                .build();

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(2L)
                .planCode("MA_THUAT")
                .name("Gói Ma Thuật")
                .targetRank("DIAMOND")
                .build();

        when(userSubscriptionRepository.findById(subId)).thenReturn(Optional.of(sub));
        when(planRepository.findById(2L)).thenReturn(Optional.of(plan));
        when(userSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserSubscriptionResponse response = subscriptionService.cancelSubscription(userId, subId, "Chi phí quá cao");

        assertThat(response).isNotNull();
        assertThat(response.getAutoRenewing()).isFalse();
        assertThat(response.getSubscriptionState()).isEqualTo(SubscriptionState.CANCELLED);
        assertThat(response.getExpiryTime()).isEqualTo(futureExpiry); // Không bị cắt ngày

        assertThat(sub.getCancellationReason()).isEqualTo("Chi phí quá cao");
        assertThat(sub.getAutoRenewing()).isFalse();

        // Kiểm tra Audit Log
        ArgumentCaptor<SubscriptionAuditLog> captor = ArgumentCaptor.forClass(SubscriptionAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        SubscriptionAuditLog audit = captor.getValue();
        assertThat(audit.getEventType()).isEqualTo("SUBSCRIPTION_CANCELLED");
        assertThat(audit.getPayload()).contains("Chi phí quá cao");
    }

    @Test
    @DisplayName("2. Bảo mật: Người dùng khác không được phép hủy gói của người khác")
    void testCancelSubscription_UnauthorizedUser() {
        Long ownerId = 1L;
        Long attackerId = 999L;
        Long subId = 10L;

        UserSubscription sub = UserSubscription.builder()
                .id(subId)
                .userId(ownerId)
                .subscriptionState(SubscriptionState.ACTIVE)
                .autoRenewing(true)
                .build();

        when(userSubscriptionRepository.findById(subId)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> subscriptionService.cancelSubscription(attackerId, subId, "Hacker"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bạn không có quyền");
    }

    @Test
    @DisplayName("3. Scheduler kiểm tra gói CANCELLED: Khi chưa đến expiryTime thì KHÔNG hạ rank và KHÔNG thu tiền tiếp")
    void testScheduler_CancelledSubscriptionRetainsPrivilegesUntilExpiry() {
        SubscriptionScheduler scheduler = new SubscriptionScheduler(
                billingService, userSubscriptionRepository, userRepository
        );

        Long userId = 100L;
        User user = User.builder().id(userId).rank(UserRank.DIAMOND).build();

        // Giả lập method existsByUserId... trả về TRUE vì expiryTime ở tương lai
        when(userSubscriptionRepository.existsByUserIdAndSubscriptionStateInAndExpiryTimeAfter(
                eq(userId), anyList(), any(LocalDateTime.class)))
                .thenReturn(true);

        scheduler.checkAndDowngradeUserRank(userId);

        // User vẫn giữ nguyên DIAMOND, không bị hạ rank!
        verify(userRepository, never()).save(any());
        assertThat(user.getUserRank()).isEqualTo(UserRank.DIAMOND);
    }

    @Test
    @DisplayName("4. Scheduler kiểm tra gói CANCELLED: Khi ĐÃ QUÁ expiryTime thì hạ rank về MEMBER")
    void testScheduler_CancelledSubscriptionDowngradesAfterExpiry() {
        SubscriptionScheduler scheduler = new SubscriptionScheduler(
                billingService, userSubscriptionRepository, userRepository
        );

        Long userId = 100L;
        User user = User.builder().id(userId).rank(UserRank.DIAMOND).build();

        // Giả lập không còn subscription active/valid nào ở tương lai
        when(userSubscriptionRepository.existsByUserIdAndSubscriptionStateInAndExpiryTimeAfter(
                eq(userId), anyList(), any(LocalDateTime.class)))
                .thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        scheduler.checkAndDowngradeUserRank(userId);

        // User bị hạ rank về MEMBER
        verify(userRepository).save(user);
        assertThat(user.getUserRank()).isEqualTo(UserRank.MEMBER);
    }
}
