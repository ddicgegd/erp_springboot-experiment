package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.modules.order.dto.response.NextStatusSimulationResponse;
import com.ddicg.erp.modules.order.model.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFulfillmentSimulationServiceTest {

    private final OrderFulfillmentSimulationService simulationService = new OrderFulfillmentSimulationService();

    @Test
    @DisplayName("Should project SHIPPING handover milestone when order is PROCESSING")
    void shouldProjectShippingHandoverWhenProcessing() {
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 19, 11, 30);
        Order order = Order.builder()
                .orderNumber("ORD-20260819-8820192")
                .currentStatus(OrderStatus.PROCESSING)
                .shippingMethod("Horizon Express (Viettel Post)")
                .build();

        NextStatusSimulationResponse simulation = simulationService.simulateNextStatus(order, baseTime);

        assertThat(simulation).isNotNull();
        assertThat(simulation.getPredictedStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(simulation.getPredictedStatusTitle()).isEqualTo("Bàn giao đơn vị vận chuyển");
        assertThat(simulation.getDiagnosticPrediction()).contains("Horizon Express / Viettel Post");
        assertThat(simulation.getEstimatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 19, 14, 0));
        assertThat(simulation.getProbabilityScore()).isGreaterThanOrEqualTo(0.85);
        assertThat(simulation.getAlternativeNextStatus()).isEqualTo("DELAYED");
    }

    @Test
    @DisplayName("Should return null for terminal states such as COMPLETED")
    void shouldReturnNullForTerminalStatus() {
        Order order = Order.builder()
                .orderNumber("ORD-COMPLETED")
                .currentStatus(OrderStatus.COMPLETED)
                .build();

        NextStatusSimulationResponse simulation = simulationService.simulateNextStatus(order, LocalDateTime.now());
        assertThat(simulation).isNull();
    }
}
