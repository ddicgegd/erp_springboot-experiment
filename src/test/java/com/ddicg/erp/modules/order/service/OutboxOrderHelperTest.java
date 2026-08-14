package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.constants.KafkaTopics;
import com.ddicg.erp.core.event.model.OutboxEvent;
import com.ddicg.erp.core.event.repository.OutboxEventRepository;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.order.model.CustomerInfo;
import com.ddicg.erp.modules.order.model.Order;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxOrderHelperTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private SecurityUtil securityUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutboxOrderHelper helper = new OutboxOrderHelper(outboxEventRepository, objectMapper, securityUtil);
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORD-1001");
        order.setTotalAmount(250000.0);
        order.setCustomerInfo(CustomerInfo.builder().customerId("101").build());

        helper.saveOrderCreatedEvent(order, "VNPAY", "NCB");

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());

        OutboxEvent event = eventCaptor.getValue();
        JsonNode payload = objectMapper.readTree(event.getPayload());

        assertEquals("ORDER_CREATED", event.getEventType());
        assertEquals(KafkaTopics.ORDER_TOPIC, event.getTopic());
        assertTrue(payload.get("roles").isArray());
        assertEquals(2, payload.get("roles").size());
        assertEquals("ADMIN", payload.get("roles").get(0).asText());
        assertEquals("USER", payload.get("roles").get(1).asText());
    }
}
