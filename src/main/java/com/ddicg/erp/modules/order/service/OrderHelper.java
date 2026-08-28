package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.constants.KafkaTopics;
import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.core.event.domainevent.OutboxEnvelopeEvent;
import com.ddicg.erp.core.event.model.OutboxEvent;
import com.ddicg.erp.core.event.repository.OutboxEventRepository;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.merchandise.model.ProductInventory;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import com.ddicg.erp.modules.merchandise.repository.ProductInventoryRepository;
import com.ddicg.erp.modules.merchandise.service.InventoryService;
import com.ddicg.erp.modules.order.dto.request.CreateOrderRequest;
import com.ddicg.erp.modules.order.event.OrderCancelledEvent;
import com.ddicg.erp.modules.order.event.OrderCreatedEvent;
import com.ddicg.erp.modules.order.event.OrderStatusChangedEvent;
import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.model.OrderItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * OrderHelper: Hợp nhất toàn bộ logic hỗ trợ nghiệp vụ cho OrderService:
 * 1. Transactional Outbox Pattern (Event publishing).
 * 2. Inventory Orchestration (Distributed Locking với Redisson chống Overselling).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHelper {

    private static final List<String> ROLE_PRIORITY = List.of(
            "SUPER_ADMIN", "ADMIN", "MANAGEMENT", "EMPLOYEE", "USER"
    );

    private static final String LOCK_PREFIX = "inventory:lock:";
    private static final long LOCK_WAIT_TIME = 10;
    private static final long LOCK_LEASE_TIME = 30;

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;
    private final SecurityUtil securityUtil;
    private final ProductInventoryRepository inventoryRepository;
    private final AttributesRepository attributesRepository;
    private final RedissonClient redissonClient;
    private final InventoryService inventoryService;

    /* =========================================================================
     * 1. OUTBOX EVENT HELPERS
     * ========================================================================= */

    private void persistAndPublishOutbox(OutboxEvent outboxEvent) {
        OutboxEvent saved = outboxEventRepository.save(outboxEvent);
        if (saved != null && saved.getId() != null) {
            applicationEventPublisher.publishEvent(new OutboxEnvelopeEvent(saved.getId()));
        }
    }

    public void saveOrderCreatedEvent(Order order, String paymentMethod, String bankCode) {
        try {
            String cid = UUID.randomUUID().toString();
            String initialStatus = order.getCurrentStatus() != null ? order.getCurrentStatus().name() : "PENDING";
            Long customerId = order.getCustomerInfo() != null ? order.getCustomerInfo().getCustomerId() : null;

            OrderCreatedEvent eventData = new OrderCreatedEvent(
                    "ORDER_CREATED",
                    order.getOrderNumber(),
                    order.getTotalAmount(),
                    "VND",
                    paymentMethod,
                    bankCode,
                    initialStatus,
                    customerId,
                    resolveCurrentRoles(),
                    securityUtil.getIpAddress(),
                    "vn",
                    LocalDateTime.now().toString(),
                    cid
            );

            String payload = objectMapper.writeValueAsString(eventData);
            persistAndPublishOutbox(OutboxEvent.builder()
                    .aggregateType("ORDER")
                    .aggregateId(order.getId())
                    .eventType("ORDER_CREATED")
                    .topic(KafkaTopics.ORDER_TOPIC)
                    .messageKey(order.getOrderNumber())
                    .payload(payload)
                    .correlationId(cid)
                    .status("PENDING")
                    .build());
            log.debug("Saved ORDER_CREATED: {}", order.getOrderNumber());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create order event", e);
        }
    }

    public void saveOrderCreatedEvent(Order order, CreateOrderRequest request) {
        saveOrderCreatedEvent(order,
                request.getPaymentMethod() != null ? request.getPaymentMethod().toString().toUpperCase() : "COD",
                request.getBankCode());
    }

    public void saveOrderStatusChangedEvent(Order order, OrderStatus prev, OrderStatus next, String note) {
        saveOrderStatusChangedEvent(order, prev, next, note, null);
    }

    public void saveOrderStatusChangedEvent(Order order, OrderStatus prev, OrderStatus next, String note, String role) {
        try {
            String cid = UUID.randomUUID().toString();
            String changedBy = securityUtil.getCurrentUsername() != null ? securityUtil.getCurrentUsername() : "SYSTEM";
            String changedByRole = role != null ? role : "admin";

            OrderStatusChangedEvent eventData = new OrderStatusChangedEvent(
                    "ORDER_STATUS_CHANGED",
                    order.getOrderNumber(),
                    prev != null ? prev.name() : "NONE",
                    next.name(),
                    prev != null ? prev.getDescription() : null,
                    next.getDescription(),
                    note != null ? note : "",
                    LocalDateTime.now().toString(),
                    changedBy,
                    changedByRole,
                    cid
            );

            String payload = objectMapper.writeValueAsString(eventData);
            persistAndPublishOutbox(OutboxEvent.builder()
                    .aggregateType("ORDER")
                    .aggregateId(order.getId())
                    .eventType("ORDER_STATUS_CHANGED")
                    .topic(KafkaTopics.ORDER_TOPIC)
                    .messageKey(order.getOrderNumber())
                    .payload(payload)
                    .correlationId(cid)
                    .status("PENDING")
                    .build());
            log.debug("Saved ORDER_STATUS_CHANGED: {} → {}", order.getOrderNumber(), next);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize status change", e);
        }
    }

    public void saveOrderCancelledEvent(Order order, String reason, boolean refundRequired) {
        try {
            String cid = UUID.randomUUID().toString();
            String cancelledBy = securityUtil.getCurrentUsername();

            OrderCancelledEvent eventData = new OrderCancelledEvent(
                    "ORDER_CANCELLED",
                    order.getOrderNumber(),
                    reason,
                    refundRequired,
                    LocalDateTime.now().toString(),
                    cancelledBy,
                    cid
            );

            String payload = objectMapper.writeValueAsString(eventData);
            persistAndPublishOutbox(OutboxEvent.builder()
                    .aggregateType("ORDER")
                    .aggregateId(order.getId())
                    .eventType("ORDER_CANCELLED")
                    .topic(KafkaTopics.ORDER_TOPIC)
                    .messageKey(order.getOrderNumber())
                    .payload(payload)
                    .correlationId(cid)
                    .status("PENDING")
                    .build());
            log.debug("Saved ORDER_CANCELLED: {}", order.getOrderNumber());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cancelled event", e);
        }
    }

    private List<String> resolveCurrentRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return List.of("ANONYMOUS");
        }

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .distinct()
                .sorted(Comparator.comparingInt(OrderHelper::rolePriority).thenComparing(Comparator.naturalOrder()))
                .toList();

        return roles.isEmpty() ? List.of("ANONYMOUS") : roles;
    }

    private static int rolePriority(String role) {
        int index = ROLE_PRIORITY.indexOf(role);
        return index >= 0 ? index : ROLE_PRIORITY.size();
    }

    /* =========================================================================
     * 2. INVENTORY ORCHESTRATION HELPERS
     * ========================================================================= */

    @Transactional
    public Map<String, Integer> reserveInventory(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> skus = items.stream()
                .map(OrderItem::getAttributesSku)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<RLock> acquiredLocks = new ArrayList<>();
        Map<String, Integer> reservedQuantities = new HashMap<>();

        try {
            // Sort SKUs để ngăn deadlock
            List<String> sortedSkus = new ArrayList<>(skus);
            Collections.sort(sortedSkus);

            for (String sku : sortedSkus) {
                RLock lock = redissonClient.getLock(LOCK_PREFIX + sku);
                boolean acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

                if (!acquired) {
                    log.error("Failed to acquire lock for SKU: {}", sku);
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                            "Hệ thống đang bận, vui lòng thử lại sau: " + sku);
                }
                acquiredLocks.add(lock);
            }

            log.debug("Acquired {} locks for order creation", sortedSkus.size());

            for (OrderItem item : items) {
                String sku = item.getAttributesSku();
                int requestedQty = item.getQuantity();

                ProductInventory inventory = inventoryRepository.findBySku(sku)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,
                                "Không tìm thấy thông tin kho cho SKU: " + sku));

                if (inventory.getAvailableQuantity() < requestedQty) {
                    log.warn("Insufficient stock for SKU: {}. Available: {}, Requested: {}",
                            sku, inventory.getAvailableQuantity(), requestedQty);
                    throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                            "Sản phẩm " + sku + " chỉ còn " + inventory.getAvailableQuantity() + " sản phẩm")
                            .with("sku", sku)
                            .with("available", inventory.getAvailableQuantity())
                            .with("requested", requestedQty);
                }

                if (Boolean.TRUE.equals(inventory.getProduct().getIsDeleted())) {
                    throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,
                            "Sản phẩm " + sku + " không còn khả dụng");
                }

                inventoryService.reserveStock(sku, requestedQty);
                reservedQuantities.put(sku, requestedQty);
                log.debug("Reserved {} units for SKU: {}", requestedQty, sku);
            }

            return reservedQuantities;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Hệ thống đang bận, vui lòng thử lại");
        } finally {
            for (RLock lock : acquiredLocks) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    log.error("Error releasing lock: {}", lock.getName(), e);
                }
            }
        }
    }

    @Transactional
    public void releaseInventory(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (OrderItem item : items) {
            try {
                String sku = item.getAttributesSku();
                int quantity = item.getQuantity();
                inventoryService.releaseReservation(sku, quantity);
                log.debug("Released {} units for SKU: {}", quantity, sku);
            } catch (Exception e) {
                log.error("Failed to release inventory for SKU: {}", item.getAttributesSku(), e);
            }
        }
    }

    @Transactional
    public void confirmReservation(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (OrderItem item : items) {
            try {
                String sku = item.getAttributesSku();
                int quantity = item.getQuantity();
                inventoryService.confirmReservation(sku, quantity);
                log.debug("Confirmed reservation for SKU: {}, Quantity: {}", sku, quantity);
            } catch (Exception e) {
                log.error("Failed to confirm reservation for SKU: {}", item.getAttributesSku(), e);
            }
        }
    }

    public ValidationResult validateCartItems(Map<String, Integer> skusWithQuantities) {
        ValidationResult result = new ValidationResult();

        for (Map.Entry<String, Integer> entry : skusWithQuantities.entrySet()) {
            String sku = entry.getKey();
            int requestedQty = entry.getValue();

            try {
                ProductInventory inventory = inventoryRepository.findBySku(sku).orElse(null);

                if (inventory == null) {
                    result.addInvalidItem(sku, "Sản phẩm không tồn tại");
                    continue;
                }

                if (Boolean.TRUE.equals(inventory.getProduct().getIsDeleted())) {
                    result.addInvalidItem(sku, "Sản phẩm đã ngừng bán");
                    continue;
                }

                if (inventory.getAvailableQuantity() < requestedQty) {
                    result.addInvalidItem(sku, "Chỉ còn " + inventory.getAvailableQuantity() + " sản phẩm");
                    continue;
                }

                result.addValidItem(sku, inventory.getAvailableQuantity());

            } catch (Exception e) {
                log.error("Error validating SKU: {}", sku, e);
                result.addInvalidItem(sku, "Lỗi kiểm tra sản phẩm");
            }
        }

        return result;
    }

    public static class ValidationResult {
        private List<ValidItem> validItems = new ArrayList<>();
        private List<InvalidItem> invalidItems = new ArrayList<>();

        public boolean isValid() {
            return invalidItems.isEmpty();
        }

        public void addValidItem(String sku, int availableQuantity) {
            validItems.add(new ValidItem(sku, availableQuantity));
        }

        public void addInvalidItem(String sku, String reason) {
            invalidItems.add(new InvalidItem(sku, reason));
        }

        public static class ValidItem {
            public ValidItem() {}
            public ValidItem(String sku, int availableQuantity) { this.sku = sku; this.availableQuantity = availableQuantity; }
            private String sku;
            private int availableQuantity;
            public String getSku() { return sku; }
            public int getAvailableQuantity() { return availableQuantity; }
        }

        public static class InvalidItem {
            public InvalidItem() {}
            public InvalidItem(String sku, String reason) { this.sku = sku; this.reason = reason; }
            private String sku;
            private String reason;
            public String getSku() { return sku; }
            public String getReason() { return reason; }
        }
    }
}
