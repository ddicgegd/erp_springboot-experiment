package com.ddicg.erp.modules.order.repository;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.model.OrderFulfillmentEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class OrderFulfillmentEventRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderFulfillmentEventRepository eventRepository;

    @Test
    @DisplayName("Should persist fulfillment event and retrieve in chronological order for an order")
    void shouldPersistAndRetrieveEventsChronologically() {
        Order order = Order.builder()
                .orderNumber("ORD-20261005-TEST01")
                .subtotal(100.0)
                .totalAmount(100.0)
                .currentStatus(OrderStatus.PROCESSING)
                .build();
        order = entityManager.persistAndFlush(order);

        OrderFulfillmentEvent event1 = OrderFulfillmentEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(OrderStatus.PENDING)
                .stageKey("ORDER_RECEIVED")
                .stageTitle("Đã tiếp nhận đơn hàng")
                .diagnosticMessage("Đơn hàng đã được hệ thống ERP xác nhận thành công")
                .actor("SYSTEM")
                .facility("ERP_CORE")
                .eventTime(LocalDateTime.of(2026, 8, 19, 9, 0))
                .build();

        OrderFulfillmentEvent event2 = OrderFulfillmentEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(OrderStatus.PROCESSING)
                .stageKey("PACKING_TESTING")
                .stageTitle("Đang đóng gói & Kiểm thử")
                .diagnosticMessage("Bộ phận kho đang kiểm tra linh kiện và đóng hộp nguyên seal")
                .actor("WAREHOUSE_STAFF")
                .facility("KHO_TONG_HN")
                .eventTime(LocalDateTime.of(2026, 8, 19, 11, 30))
                .build();

        eventRepository.save(event1);
        eventRepository.save(event2);
        entityManager.flush();
        entityManager.clear();

        List<OrderFulfillmentEvent> events = eventRepository.findByOrderIdOrderByEventTimeAsc(order.getId());
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getStageKey()).isEqualTo("ORDER_RECEIVED");
        assertThat(events.get(0).getEventTime()).isEqualTo(LocalDateTime.of(2026, 8, 19, 9, 0));
        assertThat(events.get(1).getStageKey()).isEqualTo("PACKING_TESTING");
        assertThat(events.get(1).getDiagnosticMessage()).contains("nguyên seal");
    }
}
