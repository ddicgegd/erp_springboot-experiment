package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.order.dto.response.MyOrderDetailResponse;
import com.ddicg.erp.modules.order.model.CustomerInfo;
import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.model.OrderFulfillmentEvent;
import com.ddicg.erp.modules.order.repository.OrderFulfillmentEventRepository;
import com.ddicg.erp.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class OrderJourneyIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderFulfillmentEventRepository eventRepository;

    @MockBean
    private SecurityUtil securityUtil;

    @Test
    @DisplayName("Should return full journey including real steps and simulated next milestone")
    void shouldReturnFullJourneyWithSimulatedStep() {
        User user = new User();
        user.setId(99L);
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(user));

        Order order = Order.builder()
                .orderNumber("ORD-20260819-JOURNEY")
                .subtotal(500.0)
                .totalAmount(500.0)
                .currentStatus(OrderStatus.PROCESSING)
                .shippingMethod("Horizon Express (Viettel Post)")
                .customerInfo(CustomerInfo.builder().customerId(99L).customerName("Test Buyer").build())
                .build();
        order = orderRepository.save(order);

        OrderFulfillmentEvent ev1 = OrderFulfillmentEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(OrderStatus.PENDING)
                .stageKey("ORDER_RECEIVED")
                .stageTitle("Đã tiếp nhận đơn hàng")
                .diagnosticMessage("Đơn hàng đã được hệ thống ERP xác nhận thành công")
                .eventTime(LocalDateTime.of(2026, 8, 19, 9, 0))
                .build();

        OrderFulfillmentEvent ev2 = OrderFulfillmentEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(OrderStatus.PROCESSING)
                .stageKey("PACKING_TESTING")
                .stageTitle("Đang đóng gói & Kiểm thử")
                .diagnosticMessage("Bộ phận kho đang kiểm tra linh kiện và đóng hộp nguyên seal")
                .eventTime(LocalDateTime.of(2026, 8, 19, 11, 30))
                .build();

        eventRepository.save(ev1);
        eventRepository.save(ev2);

        Response<MyOrderDetailResponse> response = orderService.getMyOrderDetail(order.getOrderNumber());

        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getDeliverySteps()).isNotNull();
        // 2 real steps + 1 simulated next step = 3 steps
        assertThat(response.getData().getDeliverySteps()).hasSize(3);

        var step1 = response.getData().getDeliverySteps().get(0);
        assertThat(step1.isCompleted()).isTrue();
        assertThat(step1.isSimulated()).isFalse();

        var step2 = response.getData().getDeliverySteps().get(1);
        assertThat(step2.isActive()).isTrue();
        assertThat(step2.isSimulated()).isFalse();

        var step3 = response.getData().getDeliverySteps().get(2);
        assertThat(step3.isSimulated()).isTrue();
        assertThat(step3.isCompleted()).isFalse();
        assertThat(step3.isFundingOrHandover()).isTrue();
        assertThat(step3.getTitle()).isEqualTo("Bàn giao đơn vị vận chuyển");
        assertThat(step3.getRiskLevel()).isEqualTo("NORMAL");
    }
}
