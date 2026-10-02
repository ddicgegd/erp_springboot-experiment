package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.payment.model.CustomerPaymentProfile;
import com.ddicg.erp.modules.payment.repository.CustomerPaymentProfileRepository;
import com.ddicg.erp.modules.payment.service.client.PaymentGatewayClient;
import com.ddicg.erp.modules.subscription.dto.request.GooglePayWebRegisterRequest;
import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GooglePayWebService {

    private final PaymentGatewayClient paymentGatewayClient;
    private final CustomerPaymentProfileRepository profileRepository;
    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserSubscriptionResponse registerTrialSubscription(Long userId, GooglePayWebRegisterRequest request) {
        log.info("Bắt đầu đăng ký gói Trial Google Pay Web cho User ID: {}, Plan: {}", userId, request.getPlanCode());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng với ID: " + userId));

        SubscriptionPlan plan = planRepository.findByPlanCode(request.getPlanCode())
                .orElseThrow(() -> new BusinessException("Gói đăng ký không tồn tại: " + request.getPlanCode()));

        // 1. Kiểm tra nếu user đã có subscription ACTIVE hoặc TRIALING
        userSubscriptionRepository.findByUserIdAndSubscriptionStateIn(userId,
                List.of(SubscriptionState.ACTIVE, SubscriptionState.TRIALING)
        ).ifPresent(existing -> {
            throw new BusinessException("Người dùng đã có gói dịch vụ đang hoạt động.");
        });

        // 2. Vaulting token Google Pay và lưu vào CustomerPaymentProfile của User
        CustomerPaymentProfile profile = paymentGatewayClient.vaultPaymentMethod(
                userId,
                request.getPaymentToken(),
                request.getCardNetwork(),
                request.getLastFour()
        );
        profile = profileRepository.save(profile);

        // 3. Khởi tạo UserSubscription với trạng thái TRIALING (30 ngày dùng thử miễn phí)
        LocalDateTime now = LocalDateTime.now();
        int trialDays = plan.getFreeTrialDays() != null ? plan.getFreeTrialDays() : 30;
        LocalDateTime expiryTime = now.plusDays(trialDays);

        UserSubscription sub = UserSubscription.builder()
                .userId(userId)
                .planId(plan.getId())
                .paymentProfileId(profile.getId())
                .subscriptionState(SubscriptionState.TRIALING)
                .isTrial(true)
                .startTime(now)
                .expiryTime(expiryTime)
                .autoRenewing(true)
                .build();

        sub = userSubscriptionRepository.save(sub);

        // 4. Nâng cấp rank cho User theo targetRank của plan
        updateUserRank(user, plan.getTargetRank());

        log.info("Kích hoạt thành công gói Trial 30 ngày cho User ID [{}], Plan [{}], Expiry [{}]",
                userId, plan.getPlanCode(), expiryTime);

        return UserSubscriptionResponse.builder()
                .id(sub.getId())
                .userId(sub.getUserId())
                .planId(plan.getId())
                .planCode(plan.getPlanCode())
                .planName(plan.getName())
                .targetRank(plan.getTargetRank())
                .billingPeriodMonths(plan.getBillingPeriodMonths())
                .subscriptionState(sub.getSubscriptionState())
                .isTrial(sub.getIsTrial())
                .startTime(sub.getStartTime())
                .expiryTime(sub.getExpiryTime())
                .autoRenewing(sub.getAutoRenewing())
                .paymentProfileId(profile.getId())
                .build();
    }

    private void updateUserRank(User user, String targetRankStr) {
        try {
            UserRank targetRank = UserRank.valueOf(targetRankStr);
            user.setRank(targetRank);
            userRepository.save(user);
            log.info("Cập nhật rank thành công cho User [{}] -> [{}]", user.getId(), targetRank);
        } catch (IllegalArgumentException e) {
            log.warn("Target rank không hợp lệ: {}", targetRankStr);
        }
    }
}
