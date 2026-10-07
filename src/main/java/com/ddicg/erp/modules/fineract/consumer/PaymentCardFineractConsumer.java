package com.ddicg.erp.modules.fineract.consumer;

import com.ddicg.erp.core.common.constants.KafkaTopics;
import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.model.Payment;
import com.ddicg.erp.modules.order.repository.OrderRepository;
import com.ddicg.erp.modules.order.repository.PaymentRepository;
import com.ddicg.erp.modules.payment.dto.CardPaymentResultEvent;
import com.ddicg.erp.modules.subscription.model.SubscriptionAuditLog;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.SubscriptionAuditLogRepository;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumer lắng nghe sự kiện thanh toán thẻ thành công từ topic payment.card.completed.
 * Thực hiện:
 * 1. Cơ chế Idempotency chống hạch toán trùng lặp (in-memory + Redis + PaymentRepository).
 * 2. Hạch toán tự động sang Apache Fineract (Doanh thu bán hàng).
 * 3. Nếu là Subscription: Cập nhật UserSubscription sang ACTIVE, gia hạn expiryTime, duy trì UserRank, ghi audit log.
 * 4. Nếu là Order thường: Hạch toán Fineract recordSale, cập nhật Order sang CONFIRMED/PAID.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.fineract.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class PaymentCardFineractConsumer {

    private final FineractJournalService fineractJournalService;
    private final OrderRepository orderRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final UserRepository userRepository;
    private final SubscriptionAuditLogRepository auditLogRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Bộ nhớ cache cục bộ để chống race-condition trong cùng tiến trình JVM.
     */
    private final Set<String> processedTransactions = ConcurrentHashMap.newKeySet();

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_CARD_COMPLETED,
            groupId = "fineract-payment-card-group",
            containerFactory = "kafkaListenerContainerFactory",
            properties = {"auto.offset.reset=earliest", "enable.auto.commit=false"}
    )
    public void consume(Object msg,
                        @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        String dedupKey = null;
        boolean redisLocked = false;

        try {
            CardPaymentResultEvent event = parsePayload(msg);
            if (event == null) {
                log.warn("⚠️ Bỏ qua message rỗng từ topic {}", KafkaTopics.PAYMENT_CARD_COMPLETED);
                return;
            }

            String transactionId = event.getTransactionId();
            dedupKey = resolveDedupKey(transactionId, event.getOrderNumber(), event.getSubscriptionId(), key);

            // 1. Kiểm tra Idempotency chống hạch toán trùng lặp
            // Bước 1a: Fast In-memory check
            if (!processedTransactions.add(dedupKey)) {
                log.warn("⚠️ [Idempotency] Giao dịch thẻ {} đã xử lý (In-memory cache). Bỏ qua để tránh trùng lặp.", dedupKey);
                return;
            }

            // Bước 1b: Redis check & lock
            if (redisTemplate != null) {
                try {
                    String redisKey = RedisTable.PAYMENT_CARD_DEDUP.key(dedupKey);
                    Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofDays(7));
                    if (Boolean.FALSE.equals(isNew)) {
                        log.warn("⚠️ [Idempotency] Giao dịch thẻ {} đã tồn tại trên Redis. Bỏ qua hạch toán trùng lặp.", dedupKey);
                        return;
                    }
                    redisLocked = true;
                } catch (Exception e) {
                    log.warn("⚠️ Lỗi kết nối Redis khi check Idempotency cho key {}. Chuyển sang kiểm tra Database: {}", dedupKey, e.getMessage());
                }
            }

            // Bước 1c: Database check
            if (transactionId != null && !transactionId.isBlank() && paymentRepository.existsByTransactionCode(transactionId)) {
                log.warn("⚠️ [Idempotency] Giao dịch thẻ {} đã được lưu trong DB Payment. Bỏ qua.", transactionId);
                return;
            }

            // 2. Phân nhánh xử lý theo loại giao dịch (Subscription vs Order)
            if (Boolean.TRUE.equals(event.getIsSubscription())) {
                processSubscriptionPayment(event);
            } else {
                processOrderPayment(event);
            }

            // 3. Đánh dấu đã xử lý thành công trên Redis
            if (redisTemplate != null && redisLocked) {
                try {
                    String redisKey = RedisTable.PAYMENT_CARD_DEDUP.key(dedupKey);
                    redisTemplate.opsForValue().set(redisKey, "PROCESSED", Duration.ofDays(30));
                } catch (Exception ignored) {}
            }

            log.info("✅ Xử lý thành công thanh toán thẻ qua Kafka cho txnId: {}, orderNumber: {}, subId: {}",
                    transactionId, event.getOrderNumber(), event.getSubscriptionId());

        } catch (Exception e) {
            log.error("❌ Lỗi khi xử lý message thanh toán thẻ từ Kafka: {}", msg, e);
            // Giải phóng lock để cho phép retry nếu có lỗi
            if (dedupKey != null) {
                processedTransactions.remove(dedupKey);
                if (redisLocked && redisTemplate != null) {
                    try {
                        redisTemplate.delete(RedisTable.PAYMENT_CARD_DEDUP.key(dedupKey));
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    /**
     * Xử lý thanh toán cho gói thuê bao (UserSubscription)
     */
    private void processSubscriptionPayment(CardPaymentResultEvent event) {
        Long subscriptionId = event.getSubscriptionId();
        if (subscriptionId == null) {
            log.error("❌ Giao dịch Subscription nhưng thiếu subscriptionId trong event: {}", event);
            return;
        }

        UserSubscription sub = userSubscriptionRepository.findById(subscriptionId).orElse(null);
        if (sub == null) {
            log.error("❌ Không tìm thấy UserSubscription với ID: {}", subscriptionId);
            return;
        }

        SubscriptionPlan plan = sub.getPlanId() != null
                ? planRepository.findById(sub.getPlanId()).orElse(null)
                : null;

        SubscriptionState oldState = sub.getSubscriptionState();

        // 1. Gia hạn expiryTime và chuyển trạng thái ACTIVE
        LocalDateTime currentExpiry = (sub.getExpiryTime() != null && sub.getExpiryTime().isAfter(LocalDateTime.now()))
                ? sub.getExpiryTime()
                : (event.getPaidAt() != null ? event.getPaidAt() : LocalDateTime.now());

        int billingMonths = (plan != null && plan.getBillingPeriodMonths() != null && plan.getBillingPeriodMonths() > 0)
                ? plan.getBillingPeriodMonths()
                : 1;

        LocalDateTime newExpiry = currentExpiry.plusMonths(billingMonths);

        sub.setSubscriptionState(SubscriptionState.ACTIVE);
        sub.setIsTrial(false);
        sub.setExpiryTime(newExpiry);
        sub.setLastPaymentTime(event.getPaidAt() != null ? event.getPaidAt() : LocalDateTime.now());

        // 2. Duy trì UserRank cho User
        if (plan != null && plan.getTargetRank() != null && !plan.getTargetRank().isBlank()) {
            userRepository.findById(sub.getUserId()).ifPresent(user -> {
                try {
                    UserRank rank = UserRank.valueOf(plan.getTargetRank());
                    user.setRank(rank);
                    userRepository.save(user);
                    log.info("⭐ Duy trì UserRank [{}] cho User ID [{}] theo gói [{}]", rank, sub.getUserId(), plan.getPlanCode());
                } catch (Exception e) {
                    log.warn("⚠️ Không thể cập nhật rank [{}] cho User [{}]: {}", plan.getTargetRank(), sub.getUserId(), e.getMessage());
                }
            });
        }

        // 3. Tự động hạch toán Fineract cho subscription
        PaymentMethod paymentMethod = resolvePaymentMethod(event.getGateway());
        BigDecimal amount = event.getGrossAmount() != null
                ? event.getGrossAmount()
                : (plan != null && plan.getPriceAmount() != null ? plan.getPriceAmount() : BigDecimal.ZERO);

        String referenceId = (event.getTransactionId() != null && !event.getTransactionId().isBlank())
                ? event.getTransactionId()
                : ("SUB-" + sub.getId() + "-" + System.currentTimeMillis());

        String planCode = plan != null ? plan.getPlanCode() : ("ID-" + sub.getPlanId());
        String note = "Hạch toán thẻ (" + (event.getGateway() != null ? event.getGateway() : "CARD") + ") gói " + planCode + " cho User " + sub.getUserId();

        String fineractJournalId = null;
        try {
            JsonNode fineractResp = fineractJournalService.recordSale(
                    referenceId,
                    amount,
                    note,
                    paymentMethod
            );
            if (fineractResp != null && fineractResp.has("entryNumber")) {
                fineractJournalId = fineractResp.get("entryNumber").asText();
            } else if (fineractResp != null && fineractResp.has("resourceId")) {
                fineractJournalId = fineractResp.get("resourceId").asText();
            } else {
                fineractJournalId = "JE-" + referenceId;
            }
            sub.setLastFineractJournalId(fineractJournalId);
            log.info("📊 Hạch toán Fineract thành công cho Subscription [{}], Journal ID: {}", sub.getId(), fineractJournalId);
        } catch (Exception e) {
            log.error("❌ Lỗi khi tự động hạch toán Fineract cho Subscription [{}]: {}", sub.getId(), e.getMessage(), e);
        }

        userSubscriptionRepository.save(sub);

        // 4. Lưu audit log cho subscription
        SubscriptionAuditLog auditLog = SubscriptionAuditLog.builder()
                .userId(sub.getUserId())
                .subscriptionId(sub.getId())
                .eventType("SUBSCRIPTION_CARD_PAYMENT_COMPLETED")
                .previousState(oldState != null ? oldState.name() : null)
                .newState(SubscriptionState.ACTIVE.name())
                .amount(amount)
                .currency(event.getCurrency() != null ? event.getCurrency() : (plan != null ? plan.getCurrency() : "VND"))
                .fineractJournalId(fineractJournalId)
                .payload("Gateway=" + event.getGateway() + ", TxnId=" + event.getTransactionId() + ", CardType=" + event.getCardType())
                .build();
        auditLogRepository.save(auditLog);

        // 5. Lưu payment record để lưu vết và chống trùng lặp
        savePaymentRecord(null, event, "Thanh toán thẻ gói thuê bao " + planCode);
    }

    /**
     * Xử lý thanh toán cho đơn hàng thường (Order)
     */
    private void processOrderPayment(CardPaymentResultEvent event) {
        String orderNumber = event.getOrderNumber();
        if (orderNumber == null || orderNumber.isBlank()) {
            log.error("❌ Đơn hàng thường nhưng thiếu orderNumber trong event: {}", event);
            return;
        }

        Order order = orderRepository.findByOrderNumber(orderNumber).orElse(null);
        if (order == null) {
            log.warn("⚠️ Không tìm thấy Order với orderNumber: {}", orderNumber);
            return;
        }

        PaymentMethod paymentMethod = resolvePaymentMethod(event.getGateway());
        BigDecimal amount = event.getGrossAmount() != null
                ? event.getGrossAmount()
                : BigDecimal.valueOf(order.getTotalAmount() != null ? order.getTotalAmount() : 0.0);

        // 1. Hạch toán Fineract recordSale
        try {
            String note = "Doanh thu bán hàng thẻ (" + (event.getGateway() != null ? event.getGateway() : "CARD") + ") đơn: " + orderNumber;
            fineractJournalService.recordSale(orderNumber, amount, note, paymentMethod);
            log.info("📊 Fineract journal recorded for SALE: order={}, amount={}, gateway={}", orderNumber, amount, event.getGateway());
        } catch (Exception e) {
            log.error("❌ Failed to record Fineract SALE journal for order: {}", orderNumber, e);
        }

        // 2. Cập nhật Order thành CONFIRMED / PAID
        OrderStatus currentStatus = order.getCurrentStatus() != null ? order.getCurrentStatus() : OrderStatus.PENDING;
        if (currentStatus != OrderStatus.CONFIRMED && currentStatus != OrderStatus.PROCESSING
                && currentStatus != OrderStatus.SHIPPING && currentStatus != OrderStatus.DELIVERED
                && currentStatus != OrderStatus.COMPLETED) {
            if (order.getStatus() == null) {
                order.setStatus(new ArrayList<>());
            }
            order.getStatus().add(OrderStatus.CONFIRMED);
            order.setCurrentStatus(OrderStatus.CONFIRMED);
            order.setConfirmedAt(event.getPaidAt() != null ? event.getPaidAt() : LocalDateTime.now());
            orderRepository.save(order);
            log.info("✅ Cập nhật Order [{}] sang trạng thái CONFIRMED (PAID)", orderNumber);
        } else {
            log.info("ℹ️ Order [{}] đã ở trạng thái [{}], giữ nguyên trạng thái hiện tại", orderNumber, currentStatus);
        }

        // 3. Lưu Payment record để lưu vết và idempotency
        savePaymentRecord(order, event, "Thanh toán thẻ đơn hàng " + orderNumber);
    }

    /**
     * Lưu Payment entity ghi vết giao dịch
     */
    private void savePaymentRecord(Order order, CardPaymentResultEvent event, String description) {
        try {
            String rawPayload = objectMapper.writeValueAsString(event);
            Payment payment = Payment.builder()
                    .order(order)
                    .provider(event.getGateway() != null ? event.getGateway() : "CARD")
                    .transactionCode(event.getTransactionId())
                    .amount(event.getGrossAmount() != null ? event.getGrossAmount().doubleValue() : (order != null ? order.getTotalAmount() : null))
                    .status("PAID")
                    .paymentDate(event.getPaidAt() != null ? event.getPaidAt() : LocalDateTime.now())
                    .description(description)
                    .rawResponse(rawPayload)
                    .cardType(event.getCardType())
                    .bankCode(event.getCardLastFour() != null ? ("****" + event.getCardLastFour()) : null)
                    .build();
            paymentRepository.save(payment);
            log.info("💳 Đã lưu Payment record thành công: txnId={}, status=PAID", event.getTransactionId());
        } catch (Exception e) {
            log.error("⚠️ Lỗi khi lưu Payment record cho transaction [{}]: {}", event.getTransactionId(), e.getMessage(), e);
        }
    }

    /**
     * Phân giải PaymentMethod từ chuỗi gateway (VNPAY / PAYPAL / thẻ khác)
     */
    private PaymentMethod resolvePaymentMethod(String gateway) {
        if (gateway == null || gateway.isBlank()) {
            return PaymentMethod.CREDIT_CARD;
        }
        String upper = gateway.trim().toUpperCase();
        if (upper.contains("PAYPAL")) {
            return PaymentMethod.PAYPAL;
        } else if (upper.contains("VNPAY")) {
            return PaymentMethod.VNPAY;
        } else {
            return PaymentMethod.CREDIT_CARD;
        }
    }

    /**
     * Xây dựng deduplication key
     */
    private String resolveDedupKey(String transactionId, String orderNumber, Long subscriptionId, String key) {
        if (transactionId != null && !transactionId.isBlank()) {
            return transactionId.trim();
        }
        if (orderNumber != null && !orderNumber.isBlank()) {
            return "ORDER:" + orderNumber.trim();
        }
        if (subscriptionId != null) {
            return "SUB:" + subscriptionId;
        }
        if (key != null && !key.isBlank()) {
            return "KEY:" + key.trim();
        }
        return "UNKNOWN:" + System.currentTimeMillis();
    }

    /**
     * Deserialize payload message thành CardPaymentResultEvent
     */
    private CardPaymentResultEvent parsePayload(Object msg) throws Exception {
        while (msg instanceof ConsumerRecord<?, ?> record) {
            msg = record.value();
        }
        if (msg == null) {
            return null;
        }
        if (msg instanceof CardPaymentResultEvent event) {
            return event;
        } else if (msg instanceof String str) {
            if (str.isBlank()) {
                return null;
            }
            return objectMapper.readValue(str, CardPaymentResultEvent.class);
        } else if (msg instanceof byte[] bytes) {
            if (bytes.length == 0) {
                return null;
            }
            return objectMapper.readValue(bytes, CardPaymentResultEvent.class);
        } else if (msg instanceof JsonNode node) {
            return objectMapper.treeToValue(node, CardPaymentResultEvent.class);
        } else {
            return objectMapper.convertValue(msg, CardPaymentResultEvent.class);
        }
    }

    /**
     * Setter phục vụ Mock trong unit test nếu cần thiết
     */
    void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
}
