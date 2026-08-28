package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.constants.KafkaTopics;
import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.core.event.model.OutboxEvent;
import com.ddicg.erp.core.event.repository.OutboxEventRepository;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import com.ddicg.erp.modules.merchandise.repository.ProductInventoryRepository;
import com.ddicg.erp.modules.merchandise.service.InventoryService;
import com.ddicg.erp.modules.order.model.CustomerInfo;
import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.model.OrderItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import com.ddicg.erp.core.event.domainevent.OutboxEnvelopeEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderHelperTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private SecurityUtil securityUtil;

    @Mock
    private ProductInventoryRepository inventoryRepository;

    @Mock
    private AttributesRepository attributesRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private InventoryService inventoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrderHelper createHelper() {
        return new OrderHelper(
                outboxEventRepository,
                applicationEventPublisher,
                objectMapper,
                securityUtil,
                inventoryRepository,
                attributesRepository,
                redissonClient,
                inventoryService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void saveOrderCreatedEventIncludesAllCurrentRoles() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "customer@example.com",
                "password",
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("SCOPE_openid")
                )
        ));

        when(securityUtil.getIpAddress()).thenReturn("127.0.0.1");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> {
            OutboxEvent ev = invocation.getArgument(0);
            ev.setId(100L);
            return ev;
        });

        OrderHelper helper = createHelper();
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORD-1001");
        order.setTotalAmount(250000.0);
        order.setCustomerInfo(CustomerInfo.builder().customerId(101L).build());

        helper.saveOrderCreatedEvent(order, "VNPAY", "NCB");

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        verify(applicationEventPublisher).publishEvent(new OutboxEnvelopeEvent(100L));

        OutboxEvent event = eventCaptor.getValue();
        JsonNode payload = objectMapper.readTree(event.getPayload());

        assertEquals("ORDER_CREATED", event.getEventType());
        assertEquals(KafkaTopics.ORDER_TOPIC, event.getTopic());
        assertTrue(payload.get("roles").isArray());
        assertEquals(2, payload.get("roles").size());
        assertEquals("ADMIN", payload.get("roles").get(0).asText());
        assertEquals("USER", payload.get("roles").get(1).asText());
        assertEquals("ORD-1001", payload.get("orderNumber").asText());
        assertEquals("PENDING", payload.get("initialStatus").asText());
        assertTrue(payload.get("orderId") == null || payload.get("orderId").isNull());
    }

    @Test
    void saveOrderStatusChangedEventPayloadDoesNotContainOrderId() throws Exception {
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> {
            OutboxEvent ev = invocation.getArgument(0);
            ev.setId(101L);
            return ev;
        });

        OrderHelper helper = createHelper();
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORD-1002");

        helper.saveOrderStatusChangedEvent(order, OrderStatus.PENDING, OrderStatus.PROCESSING, "COD auto");

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        verify(applicationEventPublisher).publishEvent(new OutboxEnvelopeEvent(101L));

        OutboxEvent event = eventCaptor.getValue();
        JsonNode payload = objectMapper.readTree(event.getPayload());

        assertEquals("ORDER_STATUS_CHANGED", event.getEventType());
        assertEquals(KafkaTopics.ORDER_TOPIC, event.getTopic());
        assertEquals("ORD-1002", payload.get("orderNumber").asText());
        assertEquals("PENDING", payload.get("previousStatus").asText());
        assertEquals("PROCESSING", payload.get("newStatus").asText());
        assertTrue(payload.get("orderId") == null || payload.get("orderId").isNull());
    }

    @Test
    void saveOrderCancelledEventPayloadContainsReason() throws Exception {
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> {
            OutboxEvent ev = invocation.getArgument(0);
            ev.setId(102L);
            return ev;
        });

        OrderHelper helper = createHelper();
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORD-1003");

        helper.saveOrderCancelledEvent(order, "Customer requested", true);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        verify(applicationEventPublisher).publishEvent(new OutboxEnvelopeEvent(102L));

        OutboxEvent event = eventCaptor.getValue();
        JsonNode payload = objectMapper.readTree(event.getPayload());

        assertEquals("ORDER_CANCELLED", event.getEventType());
        assertEquals("Customer requested", payload.get("reason").asText());
        assertTrue(payload.get("refundRequired").asBoolean());
    }

    @Test
    void releaseInventoryDelegatesToInventoryService() {
        OrderHelper helper = createHelper();
        OrderItem item = OrderItem.builder().attributesSku("SKU-1001").quantity(2).build();

        helper.releaseInventory(List.of(item));

        verify(inventoryService).releaseReservation("SKU-1001", 2);
    }

    @Test
    void confirmReservationDelegatesToInventoryService() {
        OrderHelper helper = createHelper();
        OrderItem item = OrderItem.builder().attributesSku("SKU-1001").quantity(3).build();

        helper.confirmReservation(List.of(item));

        verify(inventoryService).confirmReservation("SKU-1001", 3);
    }
}
