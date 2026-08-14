package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.event.model.OutboxEvent;
import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.modules.order.dto.request.CreateOrderRequest;
import com.ddicg.erp.core.event.repository.OutboxEventRepository;
import com.ddicg.erp.core.common.constants.KafkaTopics;
import com.ddicg.erp.core.security.SecurityUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxOrderHelper {

    private static final List<String> ROLE_PRIORITY = List.of(
            "SUPER_ADMIN", "ADMIN", "MANAGEMENT", "EMPLOYEE", "USER"
    );

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final SecurityUtil securityUtil;

    public void saveOrderCreatedEvent(Order order, String paymentMethod, String bankCode) {
        try {
            String cid = UUID.randomUUID().toString();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventType","ORDER_CREATED"); m.put("orderId",order.getOrderNumber());
            m.put("orderNumber",order.getOrderNumber()); m.put("amount",order.getTotalAmount());
            m.put("currency","VND"); m.put("paymentMethod",paymentMethod);
            m.put("bankCode",bankCode);
            m.put("customerId",order.getCustomerInfo()!=null?order.getCustomerInfo().getCustomerId():null);
            m.put("roles", resolveCurrentRoles());
            m.put("ipAddress",securityUtil.getIpAddress()); m.put("language","vn");
            m.put("createdAt",java.time.LocalDateTime.now().toString());
            m.put("correlationId",cid);
            String payload = objectMapper.writeValueAsString(m);
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType("ORDER").aggregateId(order.getId())
                    .eventType("ORDER_CREATED").topic(KafkaTopics.ORDER_TOPIC)
                    .messageKey(order.getOrderNumber()).payload(payload)
                    .correlationId(cid).status("PENDING").build());
            log.info("Saved ORDER_CREATED: {}", order.getOrderNumber());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create order event", e);
        }
    }

    public void saveOrderCreatedEvent(Order order, CreateOrderRequest request) {
        saveOrderCreatedEvent(order,
                request.getPaymentMethod()!=null?request.getPaymentMethod().toString().toUpperCase():"COD",
                request.getBankCode());
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
                .sorted(Comparator.comparingInt(OutboxOrderHelper::rolePriority).thenComparing(Comparator.naturalOrder()))
                .toList();

        return roles.isEmpty() ? List.of("ANONYMOUS") : roles;
    }

    private static int rolePriority(String role) {
        int index = ROLE_PRIORITY.indexOf(role);
        return index >= 0 ? index : ROLE_PRIORITY.size();
    }

    public void saveOrderStatusChangedEvent(Order order, OrderStatus prev, OrderStatus next, String note) {
        saveOrderStatusChangedEvent(order, prev, next, note, null);
    }

    public void saveOrderStatusChangedEvent(Order order, OrderStatus prev, OrderStatus next, String note, String role) {
        try {
            String cid = UUID.randomUUID().toString();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventType","ORDER_STATUS_CHANGED");
            m.put("orderId",order.getOrderNumber()); m.put("orderNumber",order.getOrderNumber());
            m.put("previousStatus",prev!=null?prev.name():"NONE");
            m.put("newStatus",next.name());
            m.put("previousStatusDescription",prev!=null?prev.getDescription():null);
            m.put("newStatusDescription",next.getDescription());
            m.put("note",note!=null?note:"");
            m.put("changedAt",java.time.LocalDateTime.now().toString());
            m.put("changedBy",securityUtil.getCurrentUsername()!=null?securityUtil.getCurrentUsername():"SYSTEM");
            m.put("changedByRole",role!=null?role:"admin");
            m.put("correlationId",cid);
            String payload = objectMapper.writeValueAsString(m);
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType("ORDER").aggregateId(order.getId())
                    .eventType("ORDER_STATUS_CHANGED").topic(KafkaTopics.ORDER_TOPIC)
                    .messageKey(order.getOrderNumber()).payload(payload)
                    .correlationId(cid).status("PENDING").build());
            log.info("Saved ORDER_STATUS_CHANGED: {} → {}", order.getOrderNumber(), next);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize status change", e);
        }
    }

    public void saveOrderCancelledEvent(Order order, String reason, boolean refundRequired) {
        try {
            String cid = UUID.randomUUID().toString();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventType","ORDER_CANCELLED");
            m.put("orderId",order.getOrderNumber()); m.put("orderNumber",order.getOrderNumber());
            m.put("reason",reason); m.put("refundRequired",refundRequired);
            m.put("cancelledAt",java.time.LocalDateTime.now().toString());
            m.put("cancelledBy",securityUtil.getCurrentUsername());
            m.put("correlationId",cid);
            String payload = objectMapper.writeValueAsString(m);
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType("ORDER").aggregateId(order.getId())
                    .eventType("ORDER_CANCELLED").topic(KafkaTopics.ORDER_TOPIC)
                    .messageKey(order.getOrderNumber()).payload(payload)
                    .correlationId(cid).status("PENDING").build());
            log.info("Saved ORDER_CANCELLED: {}", order.getOrderNumber());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cancelled event", e);
        }
    }
}
