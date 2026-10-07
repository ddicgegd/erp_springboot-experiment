package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
    private final FineractJournalService fineractJournalService;

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

        // 3. Khởi tạo UserSubscription: Nếu có free trial (Ánh Kim 7d) -> TRIALING; nếu không (Ma Thuật) -> thu tiền ngay & ACTIVE
        LocalDateTime now = LocalDateTime.now();
        int trialDays = plan.getFreeTrialDays() != null ? plan.getFreeTrialDays() : 0;
        boolean hasTrial = trialDays > 0;
        LocalDateTime expiryTime = hasTrial
                ? now.plusDays(trialDays)
                : now.plusMonths(plan.getBillingPeriodMonths() != null ? plan.getBillingPeriodMonths() : 1);
        SubscriptionState initState = hasTrial ? SubscriptionState.TRIALING : SubscriptionState.ACTIVE;

        String fineractJournalId = null;
        if (!hasTrial) {
            String referenceId = "INIT-SUB-" + userId + "-" + System.currentTimeMillis();
            PaymentGatewayClient.MitChargeResult chargeResult = paymentGatewayClient.chargeRecurringMit(
                    profile.getMultiUseToken(),
                    plan.getPriceAmount(),
                    plan.getCurrency(),
                    referenceId
            );
            if (!chargeResult.success()) {
                throw new BusinessException("Thanh toán kích hoạt gói " + plan.getName() + " thất bại: " + chargeResult.errorMessage());
            }

            try {
                String note = "Thanh toán kích hoạt gói " + plan.getPlanCode() + " cho User " + userId;
                JsonNode fineractResp = fineractJournalService.recordSale(
                        referenceId,
                        plan.getPriceAmount(),
                        note,
                        PaymentMethod.CREDIT_CARD
                );
                if (fineractResp != null && fineractResp.has("entryNumber")) {
                    fineractJournalId = fineractResp.get("entryNumber").asText();
                } else if (fineractResp != null && fineractResp.has("resourceId")) {
                    fineractJournalId = fineractResp.get("resourceId").asText();
                } else {
                    fineractJournalId = "JE-" + referenceId;
                }
            } catch (Exception e) {
                log.error("Lỗi hạch toán Fineract cho gói kích hoạt ngay của User [{}]: {}", userId, e.getMessage());
            }
        }

        UserSubscription sub = UserSubscription.builder()
                .userId(userId)
                .planId(plan.getId())
                .paymentProfileId(profile.getProfileCode())
                .subscriptionState(initState)
                .isTrial(hasTrial)
                .startTime(now)
                .expiryTime(expiryTime)
                .autoRenewing(true)
                .lastPaymentTime(!hasTrial ? now : null)
                .lastFineractJournalId(fineractJournalId)
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
                .paymentProfileId(profile.getProfileCode())
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
