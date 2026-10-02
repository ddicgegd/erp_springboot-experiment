package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.payment.model.CustomerPaymentProfile;
import com.ddicg.erp.modules.payment.repository.CustomerPaymentProfileRepository;
import com.ddicg.erp.modules.payment.service.client.PaymentGatewayClient;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionBillingService {

    private final PaymentGatewayClient paymentGatewayClient;
    private final FineractJournalService fineractJournalService;
    private final CustomerPaymentProfileRepository profileRepository;
    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public boolean processRecurringRenewal(Long subscriptionId) {
        log.info("Bắt đầu xử lý gia hạn định kỳ MIT cho Subscription ID: {}", subscriptionId);

        UserSubscription sub = userSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy Subscription với ID: " + subscriptionId));

        SubscriptionPlan plan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy Gói dịch vụ với ID: " + sub.getPlanId()));

        CustomerPaymentProfile profile = profileRepository.findById(sub.getPaymentProfileId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy Payment Profile: " + sub.getPaymentProfileId()));

        SubscriptionState oldState = sub.getSubscriptionState();
        BigDecimal amount = plan.getPriceAmount();
        String currency = plan.getCurrency();
        String referenceId = "SUB-" + sub.getId() + "-" + System.currentTimeMillis();

        // 1. Thực hiện trừ tiền định kỳ do người bán khởi tạo (MIT Charge)
        PaymentGatewayClient.MitChargeResult chargeResult = paymentGatewayClient.chargeRecurringMit(
                profile.getMultiUseToken(),
                amount,
                currency,
                referenceId
        );

        if (!chargeResult.success()) {
            log.warn("Trừ tiền định kỳ thất bại cho Subscription [{}]: Mã lỗi [{}], Lý do [{}]",
                    subscriptionId, chargeResult.errorCode(), chargeResult.errorMessage());

            sub.setSubscriptionState(SubscriptionState.IN_GRACE_PERIOD);
            userSubscriptionRepository.save(sub);

            saveAuditLog(sub.getUserId(), sub.getId(), "MIT_CHARGE_FAILED", oldState,
                    SubscriptionState.IN_GRACE_PERIOD, amount, currency, null, chargeResult.errorMessage());

            return false;
        }

        // 2. Trừ tiền thành công -> Gia hạn thời gian và chuyển sang ACTIVE
        LocalDateTime currentExpiry = sub.getExpiryTime() != null && sub.getExpiryTime().isAfter(LocalDateTime.now())
                ? sub.getExpiryTime()
                : LocalDateTime.now();
        LocalDateTime newExpiry = currentExpiry.plusMonths(plan.getBillingPeriodMonths());

        sub.setSubscriptionState(SubscriptionState.ACTIVE);
        sub.setIsTrial(false);
        sub.setExpiryTime(newExpiry);
        sub.setLastPaymentTime(LocalDateTime.now());

        // Đảm bảo duy trì rank cho User
        userRepository.findById(sub.getUserId()).ifPresent(user -> {
            try {
                UserRank rank = UserRank.valueOf(plan.getTargetRank());
                user.setRank(rank);
                userRepository.save(user);
            } catch (Exception ignored) {}
        });

        // 3. TỰ ĐỘNG HẠCH TOÁN DOANH THU QUA APACHE FINERACT (Done -> Accounting)
        String fineractJournalId = null;
        try {
            String note = "Thanh toán định kỳ gói " + plan.getPlanCode() + " cho User " + sub.getUserId();
            JsonNode fineractResp = fineractJournalService.recordSale(
                    referenceId,
                    amount,
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
            sub.setLastFineractJournalId(fineractJournalId);
            log.info("Hạch toán Fineract thành công cho Subscription [{}], Journal ID [{}]", subscriptionId, fineractJournalId);
        } catch (Exception e) {
            log.error("Lỗi khi tự động hạch toán Fineract cho Subscription [{}]: {}", subscriptionId, e.getMessage(), e);
        }

        userSubscriptionRepository.save(sub);

        saveAuditLog(sub.getUserId(), sub.getId(), "SUBSCRIPTION_RENEWED", oldState,
                SubscriptionState.ACTIVE, amount, currency, fineractJournalId, "Gia hạn thành công qua Google Pay Web MIT");

        return true;
    }

    private void saveAuditLog(Long userId, Long subId, String eventType, SubscriptionState oldState,
                              SubscriptionState newState, BigDecimal amount, String currency,
                              String journalId, String payload) {
        SubscriptionAuditLog logEntry = SubscriptionAuditLog.builder()
                .userId(userId)
                .subscriptionId(subId)
                .eventType(eventType)
                .previousState(oldState != null ? oldState.name() : null)
                .newState(newState != null ? newState.name() : null)
                .amount(amount)
                .currency(currency)
                .fineractJournalId(journalId)
                .payload(payload)
                .build();
        auditLogRepository.save(logEntry);
    }
}
