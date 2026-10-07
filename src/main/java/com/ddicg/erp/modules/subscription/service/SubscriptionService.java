package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.subscription.dto.request.OneTimeSubscriptionPurchaseRequest;
import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.SubscriptionAuditLog;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.SubscriptionAuditLogRepository;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserRepository userRepository;
    private final FineractJournalService fineractJournalService;
    private final SubscriptionAuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getActivePlans() {
        return planRepository.findByIsActiveTrue();
    }

    @Transactional(readOnly = true)
    public UserSubscriptionResponse getMySubscription(Long userId) {
        UserSubscription sub = userSubscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElse(null);

        if (sub == null) {
            return null;
        }

        SubscriptionPlan plan = planRepository.findById(sub.getPlanId()).orElse(null);

        return UserSubscriptionResponse.builder()
                .id(sub.getId())
                .userId(sub.getUserId())
                .planId(sub.getPlanId())
                .planCode(plan != null ? plan.getPlanCode() : null)
                .planName(plan != null ? plan.getName() : null)
                .targetRank(plan != null ? plan.getTargetRank() : null)
                .billingPeriodMonths(plan != null ? plan.getBillingPeriodMonths() : null)
                .subscriptionState(sub.getSubscriptionState())
                .isTrial(sub.getIsTrial())
                .startTime(sub.getStartTime())
                .expiryTime(sub.getExpiryTime())
                .autoRenewing(sub.getAutoRenewing())
                .paymentProfileId(sub.getPaymentProfileId())
                .build();
    }

    @Transactional
    public UserSubscriptionResponse purchaseOneTimeSubscription(Long userId, OneTimeSubscriptionPurchaseRequest request) {
        log.info("Bắt đầu xử lý mua gói 1 lần cho User [{}], Gói [{}], Cổng [{}]",
                userId, request.getPlanCode(), request.getPaymentMethod());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng với ID: " + userId));

        SubscriptionPlan plan = planRepository.findByPlanCode(request.getPlanCode())
                .orElseThrow(() -> new BusinessException("Không tìm thấy gói dịch vụ với mã: " + request.getPlanCode()));

        LocalDateTime now = LocalDateTime.now();

        // Kiểm tra xem User đã có subscription active nào chưa để cộng dồn thời hạn
        List<SubscriptionState> activeStates = List.of(
                SubscriptionState.ACTIVE,
                SubscriptionState.TRIALING,
                SubscriptionState.IN_GRACE_PERIOD
        );
        Optional<UserSubscription> existingSubOpt = userSubscriptionRepository.findByUserIdAndSubscriptionStateIn(userId, activeStates);

        UserSubscription sub;
        LocalDateTime baseTime;
        if (existingSubOpt.isPresent()) {
            sub = existingSubOpt.get();
            baseTime = sub.getExpiryTime() != null && sub.getExpiryTime().isAfter(now)
                    ? sub.getExpiryTime()
                    : now;
            log.info("User [{}] đã có Subscription active [{}], cộng dồn tiếp từ {}", userId, sub.getId(), baseTime);
        } else {
            sub = UserSubscription.builder()
                    .userId(userId)
                    .startTime(now)
                    .build();
            baseTime = now;
        }

        // Hạn sử dụng: chu kỳ mua + ngày khuyến mãi (freeTrialDays)
        int billingMonths = plan.getBillingPeriodMonths() != null ? plan.getBillingPeriodMonths() : 1;
        int bonusTrialDays = plan.getFreeTrialDays() != null ? plan.getFreeTrialDays() : 0;
        LocalDateTime newExpiry = baseTime.plusMonths(billingMonths).plusDays(bonusTrialDays);

        String refId = request.getTransactionReference() != null && !request.getTransactionReference().isBlank()
                ? request.getTransactionReference().trim()
                : ("ONETIME-" + userId + "-" + System.currentTimeMillis());

        sub.setPlanId(plan.getId());
        sub.setSubscriptionState(SubscriptionState.ACTIVE);
        sub.setIsTrial(false);
        sub.setAutoRenewing(false); // Mua 1 lần -> Không tự động gia hạn
        sub.setExpiryTime(newExpiry);
        sub.setLastPaymentTime(now);
        sub.setPaymentProfileId(refId);

        // Hạch toán doanh thu sang Apache Fineract
        String fineractJournalId = null;
        try {
            String note = request.getNote() != null && !request.getNote().isBlank()
                    ? request.getNote()
                    : ("Thanh toan mua 1 lan goi " + plan.getPlanCode() + " User " + userId);
            JsonNode fineractResp = fineractJournalService.recordSale(
                    refId,
                    plan.getPriceAmount(),
                    note,
                    request.getPaymentMethod()
            );
            if (fineractResp != null && fineractResp.has("transactionId")) {
                fineractJournalId = fineractResp.get("transactionId").asText();
            } else if (fineractResp != null && fineractResp.has("resourceId")) {
                fineractJournalId = fineractResp.get("resourceId").asText();
            } else {
                fineractJournalId = "JE-" + refId;
            }
            sub.setLastFineractJournalId(fineractJournalId);
            log.info("Hạch toán Fineract thành công cho mua gói 1 lần: Journal ID [{}]", fineractJournalId);
        } catch (Exception e) {
            log.error("Lỗi khi hạch toán Fineract cho mua gói 1 lần User [{}]: {}", userId, e.getMessage(), e);
        }

        UserSubscription saved = userSubscriptionRepository.save(sub);

        // Nâng rank cho User ngay lập tức
        try {
            UserRank targetRank = UserRank.valueOf(plan.getTargetRank());
            user.setRank(targetRank);
            userRepository.save(user);
            log.info("Đã nâng rank User [{}] lên [{}] sau khi thanh toán 1 lần", userId, targetRank);
        } catch (Exception e) {
            log.warn("Không thể cập nhật rank cho User [{}]: {}", userId, e.getMessage());
        }

        // Ghi Audit Log
        saveAuditLog(userId, saved.getId(), "ONE_TIME_PURCHASE",
                SubscriptionState.ACTIVE, plan.getPriceAmount(), plan.getCurrency(),
                fineractJournalId, "Mua gói 1 lần qua " + request.getPaymentMethod() + " (" + billingMonths + " tháng + " + bonusTrialDays + " ngày khuyến mãi)");

        return UserSubscriptionResponse.builder()
                .id(saved.getId())
                .userId(saved.getUserId())
                .planId(saved.getPlanId())
                .planCode(plan.getPlanCode())
                .planName(plan.getName())
                .targetRank(plan.getTargetRank())
                .billingPeriodMonths(plan.getBillingPeriodMonths())
                .subscriptionState(saved.getSubscriptionState())
                .isTrial(saved.getIsTrial())
                .startTime(saved.getStartTime())
                .expiryTime(saved.getExpiryTime())
                .autoRenewing(saved.getAutoRenewing())
                .paymentProfileId(saved.getPaymentProfileId())
                .build();
    }

    @Transactional
    public UserSubscriptionResponse cancelSubscription(Long userId, Long subscriptionId, String reason) {
        log.info("Xử lý yêu cầu hủy gia hạn gói cho User ID [{}], Subscription ID [{}]", userId, subscriptionId);

        UserSubscription sub = userSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy Subscription với ID: " + subscriptionId));

        if (!sub.getUserId().equals(userId)) {
            throw new BusinessException("Bạn không có quyền thao tác trên gói dịch vụ này");
        }

        if (sub.getSubscriptionState() == SubscriptionState.EXPIRED) {
            throw new BusinessException("Gói dịch vụ đã hết hạn, không thể thao tác hủy");
        }

        // Tắt tính năng tự động gia hạn và ghi nhận lý do hủy (FTC Click-to-Cancel compliance)
        // Người dùng vẫn giữ nguyên quyền lợi VIP và UserRank cho đến hết expiryTime
        sub.setAutoRenewing(false);
        sub.setSubscriptionState(SubscriptionState.CANCELLED);
        sub.setCancellationReason(reason != null && !reason.isBlank() ? reason : "Người dùng chủ động hủy gia hạn");

        UserSubscription saved = userSubscriptionRepository.save(sub);

        // Ghi Audit Log
        saveAuditLog(userId, saved.getId(), "SUBSCRIPTION_CANCELLED",
                SubscriptionState.CANCELLED, BigDecimal.ZERO, "VND",
                null, "Hủy gia hạn: " + sub.getCancellationReason() + " (Quyền lợi duy trì đến: " + sub.getExpiryTime() + ")");

        log.info("✅ Đã hủy tự động gia hạn cho Subscription ID [{}]. Quyền lợi được duy trì đến {}",
                sub.getId(), sub.getExpiryTime());

        SubscriptionPlan plan = planRepository.findById(saved.getPlanId()).orElse(null);

        return UserSubscriptionResponse.builder()
                .id(saved.getId())
                .userId(saved.getUserId())
                .planId(saved.getPlanId())
                .planCode(plan != null ? plan.getPlanCode() : null)
                .planName(plan != null ? plan.getName() : null)
                .targetRank(plan != null ? plan.getTargetRank() : null)
                .billingPeriodMonths(plan != null ? plan.getBillingPeriodMonths() : null)
                .subscriptionState(saved.getSubscriptionState())
                .isTrial(saved.getIsTrial())
                .startTime(saved.getStartTime())
                .expiryTime(saved.getExpiryTime())
                .autoRenewing(saved.getAutoRenewing())
                .paymentProfileId(saved.getPaymentProfileId())
                .build();
    }

    private void saveAuditLog(Long userId, Long subId, String eventType,
                              SubscriptionState newState, BigDecimal amount, String currency,
                              String journalId, String payload) {
        SubscriptionAuditLog logEntry = SubscriptionAuditLog.builder()
                .userId(userId)
                .subscriptionId(subId)
                .eventType(eventType)
                .previousState(null)
                .newState(newState != null ? newState.name() : null)
                .amount(amount)
                .currency(currency)
                .fineractJournalId(journalId)
                .payload(payload)
                .build();
        auditLogRepository.save(logEntry);
    }
}
