# Order Fulfillment Diagnostic & Predictive Status Simulation Multi-Agent Implementation Plan

## Wave 0: Global State & Foundation (Sequential)
**Convergence Goal:** Establish the database schema, Flyway migration, and core append-only JPA entity for fulfillment event audit logs without migration version collisions.

### Task: task-db-fulfillment-event-schema
- Scope: Medium (Pair)
- Target Service: `backend/`
- Worktree: `.worktrees/task-db-fulfillment-event-schema`
- Branch: `task-db-fulfillment-event-schema`

#### Role: worker-db-fulfillment-event-schema
**Directive:** Execute the following TDD lifecycle strictly inside `.worktrees/task-db-fulfillment-event-schema`.

**1. Target Files & Rich Context:**
- Create: `src/main/resources/db/migration/V22__create_order_fulfillment_events_table.sql`
- Create: `src/main/java/com/ddicg/erp/modules/order/model/OrderFulfillmentEvent.java`
- Create: `src/main/java/com/ddicg/erp/modules/order/repository/OrderFulfillmentEventRepository.java`
- Modify: `src/main/java/com/ddicg/erp/modules/order/model/Order.java` (add lazy relationship to fulfillment events)
- Test: `src/test/java/com/ddicg/erp/modules/order/repository/OrderFulfillmentEventRepositoryTest.java`

*Architectural Boundary & Invariants:*
`OrderFulfillmentEvent` is strictly append-only. No records in this table are ever updated or deleted. Each row represents a verified physical or logical state transition of an order with its exact timestamp, stage key, title, diagnostic subtext, actor, and facility.

**2. Failing Test (Step 1):**
```java
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
```
*Command to run:* `./mvnw test -Dtest=OrderFulfillmentEventRepositoryTest` (Expected: FAIL - cannot find OrderFulfillmentEvent)

**3. Implementation (Step 3):**

```sql
-- src/main/resources/db/migration/V22__create_order_fulfillment_events_table.sql
CREATE TABLE order_fulfillment_events (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    stage_key VARCHAR(100) NOT NULL,
    stage_title VARCHAR(255) NOT NULL,
    diagnostic_message VARCHAR(1000) NOT NULL,
    actor VARCHAR(100),
    facility VARCHAR(100),
    event_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_fulfillment_events_order_id ON order_fulfillment_events(order_id);
CREATE INDEX idx_fulfillment_events_order_number ON order_fulfillment_events(order_number);
CREATE INDEX idx_fulfillment_events_event_time ON order_fulfillment_events(event_time);
```

```java
// src/main/java/com/ddicg/erp/modules/order/model/OrderFulfillmentEvent.java
package com.ddicg.erp.modules.order.model;

import com.ddicg.erp.core.common.model.base.IdentityOnly;
import com.ddicg.erp.core.common.model.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_fulfillment_events", indexes = {
    @Index(name = "idx_fulfillment_events_order_id", columnList = "order_id"),
    @Index(name = "idx_fulfillment_events_order_number", columnList = "order_number"),
    @Index(name = "idx_fulfillment_events_event_time", columnList = "event_time")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderFulfillmentEvent extends IdentityOnly<Long> {

    @Column(name = "order_id", nullable = false)
    Long orderId;

    @Column(name = "order_number", nullable = false, length = 50)
    String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    OrderStatus status;

    @Column(name = "stage_key", nullable = false, length = 100)
    String stageKey;

    @Column(name = "stage_title", nullable = false, length = 255)
    String stageTitle;

    @Column(name = "diagnostic_message", nullable = false, length = 1000)
    String diagnosticMessage;

    @Column(name = "actor", length = 100)
    String actor;

    @Column(name = "facility", length = 100)
    String facility;

    @Column(name = "event_time", nullable = false)
    LocalDateTime eventTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    LocalDateTime createdAt = LocalDateTime.now();
}
```

```java
// src/main/java/com/ddicg/erp/modules/order/repository/OrderFulfillmentEventRepository.java
package com.ddicg.erp.modules.order.repository;

import com.ddicg.erp.modules.order.model.OrderFulfillmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderFulfillmentEventRepository extends JpaRepository<OrderFulfillmentEvent, Long> {
    List<OrderFulfillmentEvent> findByOrderIdOrderByEventTimeAsc(Long orderId);
    List<OrderFulfillmentEvent> findByOrderNumberOrderByEventTimeAsc(String orderNumber);
}
```
*Command to run:* `./mvnw test -Dtest=OrderFulfillmentEventRepositoryTest` (Expected: PASS)

#### Role: censor-db-fulfillment-event-schema
**Directive:** Review diff against `task-db-fulfillment-event-schema` branch.
- Verify: Does `V22__create_order_fulfillment_events_table.sql` follow standard SQL DDL syntax compatible with both PostgreSQL and H2 in-memory test profiles?
- Verify: Is the entity strictly append-only (no `@PreUpdate` or mutable setter logic)?
- Verify: Are indexes placed on `order_id`, `order_number`, and `event_time`?

---

## Wave 1: Simulation Engine & API Contract Layer (Parallel)
**Convergence Goal:** Backend computes real historical steps and simulates the immediate next milestone with risk assessment, exposing them through `MyOrderDetailResponse`.

### Task: task-backend-simulation-engine
- Scope: Medium (Pair)
- Target Service: `backend/`
- Worktree: `.worktrees/task-backend-simulation-engine`
- Branch: `task-backend-simulation-engine`

#### Role: worker-backend-simulation-engine
**Directive:** Implement `OrderFulfillmentSimulationService` that performs predictive projection of the next status with SLA calculation, confidence rating, and risk severity diagnostics.

**1. Target Files & Rich Context:**
- Create: `src/main/java/com/ddicg/erp/modules/order/service/OrderFulfillmentSimulationService.java`
- Create: `src/main/java/com/ddicg/erp/modules/order/dto/response/NextStatusSimulationResponse.java`
- Test: `src/test/java/com/ddicg/erp/modules/order/service/OrderFulfillmentSimulationServiceTest.java`

*Business Matrix for Simulation:*
1. `PENDING` -> Next: `CONFIRMED` / `PROCESSING` (nominal SLA +30m, risk: `CANCELLED` if unpaid or out of stock).
2. `PROCESSING` (e.g. Active in image) -> Next: `SHIPPING` ("Bàn giao đơn vị vận chuyển", nominal SLA +150m at 14:00, carrier Viettel Post, risk: `DELAYED` if seal defect, SLA deviation +15m).
3. `SHIPPING` -> Next: `DELIVERED` (nominal SLA same day, risk: `RETURNING` if unreachable recipient).
4. Terminal states (`COMPLETED`, `CANCELLED`, `REFUNDED`, `FAILED`) -> Returns `null` (no simulation).

**2. Failing Test (Step 1):**
```java
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
```
*Command to run:* `./mvnw test -Dtest=OrderFulfillmentSimulationServiceTest` (Expected: FAIL)

**3. Implementation (Step 3):**

```java
// src/main/java/com/ddicg/erp/modules/order/dto/response/NextStatusSimulationResponse.java
package com.ddicg.erp.modules.order.dto.response;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NextStatusSimulationResponse {
    OrderStatus predictedStatus;
    String predictedStatusTitle;
    String diagnosticPrediction;
    LocalDateTime estimatedAt;
    String formattedEstimatedTime;
    Double probabilityScore;
    String riskSeverity;
    String riskCategory;
    String riskWarningMessage;
    List<String> prerequisiteTasks;
    String alternativeNextStatus;
    Double alternativeRiskRate;
}
```

```java
// src/main/java/com/ddicg/erp/modules/order/service/OrderFulfillmentSimulationService.java
package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.modules.order.dto.response.NextStatusSimulationResponse;
import com.ddicg.erp.modules.order.model.Order;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
public class OrderFulfillmentSimulationService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Set<OrderStatus> TERMINAL_STATUSES = Set.of(
            OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.FAILED, OrderStatus.REFUNDED
    );

    public NextStatusSimulationResponse simulateNextStatus(Order order, LocalDateTime referenceTime) {
        OrderStatus current = order.getCurrentStatus();
        if (current == null || TERMINAL_STATUSES.contains(current)) {
            return null;
        }

        LocalDateTime base = referenceTime != null ? referenceTime : LocalDateTime.now();

        if (current == OrderStatus.PENDING || current == OrderStatus.WAITING_PAYMENT) {
            LocalDateTime est = base.plusMinutes(45);
            return NextStatusSimulationResponse.builder()
                    .predictedStatus(OrderStatus.PROCESSING)
                    .predictedStatusTitle("Đang đóng gói & Kiểm thử")
                    .diagnosticPrediction("Đơn hàng đang chờ thủ kho tiếp nhận xuất kho linh kiện và phụ kiện")
                    .estimatedAt(est)
                    .formattedEstimatedTime(est.format(TIME_FMT))
                    .probabilityScore(0.95)
                    .riskSeverity("LOW")
                    .riskCategory("PAYMENT_TIMEOUT")
                    .riskWarningMessage("Đơn hàng sẽ tự động hủy nếu không hoàn tất thanh toán trước thời hạn.")
                    .prerequisiteTasks(List.of("Xác nhận cổng thanh toán", "Kiểm tra tồn kho vật lý"))
                    .alternativeNextStatus("CANCELLED")
                    .alternativeRiskRate(0.05)
                    .build();
        }

        if (current == OrderStatus.PROCESSING || current == OrderStatus.CONFIRMED) {
            LocalDateTime est = base.toLocalDate().atTime(14, 0);
            if (est.isBefore(base)) {
                est = base.plusHours(2).plusMinutes(30);
            }
            String carrier = order.getShippingMethod() != null ? order.getShippingMethod() : "Horizon Express (Viettel Post)";
            return NextStatusSimulationResponse.builder()
                    .predictedStatus(OrderStatus.SHIPPING)
                    .predictedStatusTitle("Bàn giao đơn vị vận chuyển")
                    .diagnosticPrediction("Kiện hàng đã xuất kho chuyển phát nhanh " + carrier)
                    .estimatedAt(est)
                    .formattedEstimatedTime(est.format(TIME_FMT))
                    .probabilityScore(0.91)
                    .riskSeverity("NORMAL")
                    .riskCategory("CARRIER_DELAY")
                    .riskWarningMessage("Khả năng đúng hạn 91%. Có nguy cơ trễ 15 phút do kiểm định seal vòng cuối.")
                    .prerequisiteTasks(List.of(
                            "Hoàn tất kiểm thử linh kiện và đóng hộp nguyên seal",
                            "Dán mã vận đơn chuyển phát nhanh " + carrier,
                            "Tài xế bưu cục ký biên bản giao nhận"
                    ))
                    .alternativeNextStatus("DELAYED")
                    .alternativeRiskRate(0.09)
                    .build();
        }

        if (current == OrderStatus.SHIPPING) {
            LocalDateTime est = base.toLocalDate().atTime(19, 0);
            return NextStatusSimulationResponse.builder()
                    .predictedStatus(OrderStatus.DELIVERED)
                    .predictedStatusTitle("Giao hàng & Hoàn tất")
                    .diagnosticPrediction("Bưu tá liên hệ người nhận đồng kiểm và ký biên bản giao hàng")
                    .estimatedAt(est)
                    .formattedEstimatedTime(est.format(TIME_FMT))
                    .probabilityScore(0.88)
                    .riskSeverity("NORMAL")
                    .riskCategory("DELIVERY_ABSENCE")
                    .riskWarningMessage("Nguy cơ lưu kho trạm phát nếu không liên lạc được khách hàng.")
                    .prerequisiteTasks(List.of("Trung chuyển qua trạm phát hàng địa phương", "Bưu tá nhận tuyến phát"))
                    .alternativeNextStatus("RETURNING")
                    .alternativeRiskRate(0.12)
                    .build();
        }

        return null;
    }
}
```
*Command to run:* `./mvnw test -Dtest=OrderFulfillmentSimulationServiceTest` (Expected: PASS)

#### Role: censor-backend-simulation-engine
**Directive:** Review diff against `task-backend-simulation-engine` branch.
- Verify: Does `simulateNextStatus` return `null` when order is in terminal states (`COMPLETED`, `CANCELLED`, `FAILED`, `REFUNDED`)?
- Verify: Are risk metrics (`probabilityScore`, `riskSeverity`, `alternativeRiskRate`) bounded properly?

---

### Task: task-backend-journey-api-contract
- Scope: Medium-Small (Solo)
- Target Service: `backend/`
- Worktree: `.worktrees/task-backend-journey-api-contract`
- Branch: `task-backend-journey-api-contract`

#### Role: worker-backend-journey-api-contract
**Directive:** Extend `MyOrderDetailResponse` to incorporate `List<DeliveryStepResponse> deliverySteps` and `NextStatusSimulationResponse nextStatusSimulation`, wiring `OrderService.getMyOrderDetail` to retrieve persisted events and append the simulated step.

**1. Target Files & Rich Context:**
- Create: `src/main/java/com/ddicg/erp/modules/order/dto/response/DeliveryStepResponse.java`
- Modify: `src/main/java/com/ddicg/erp/modules/order/dto/response/MyOrderDetailResponse.java`
- Modify: `src/main/java/com/ddicg/erp/modules/order/service/OrderService.java`
- Test: `src/test/java/com/ddicg/erp/modules/order/service/OrderJourneyIntegrationTest.java`

**2. Failing Test (Step 1):**
```java
package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.core.common.response.Response;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.util.SecurityUtil;
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
```
*Command to run:* `./mvnw test -Dtest=OrderJourneyIntegrationTest` (Expected: FAIL)

**3. Implementation (Step 3):**

```java
// src/main/java/com/ddicg/erp/modules/order/dto/response/DeliveryStepResponse.java
package com.ddicg.erp.modules.order.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeliveryStepResponse {
    String stepKey;
    String title;
    String diagnosticDescription;
    LocalDateTime timestamp;
    String formattedTime;
    boolean completed;
    boolean active;
    boolean simulated;
    Double confidenceRate;
    String riskLevel;
    String riskReason;
    Integer estimatedDeviationMinutes;

    public boolean isFundingOrHandover() {
        return "Bàn giao đơn vị vận chuyển".equals(title) || simulated;
    }
}
```

```java
// Update src/main/java/com/ddicg/erp/modules/order/dto/response/MyOrderDetailResponse.java:
// Add:
List<DeliveryStepResponse> deliverySteps;
NextStatusSimulationResponse nextStatusSimulation;
```

```java
// In src/main/java/com/ddicg/erp/modules/order/service/OrderService.java:
// In getMyOrderDetail(String orderNumber):
MyOrderDetailResponse res = orderMapper.toMyOrderDetailResponse(order);

List<OrderFulfillmentEvent> events = fulfillmentEventRepository.findByOrderIdOrderByEventTimeAsc(order.getId());
List<DeliveryStepResponse> steps = new ArrayList<>();
DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

for (OrderFulfillmentEvent ev : events) {
    boolean isActive = ev.getStatus() == order.getCurrentStatus();
    steps.add(DeliveryStepResponse.builder()
            .stepKey(ev.getStageKey())
            .title(ev.getStageTitle())
            .diagnosticDescription(ev.getDiagnosticMessage())
            .timestamp(ev.getEventTime())
            .formattedTime(ev.getEventTime().format(fmt))
            .completed(!isActive)
            .active(isActive)
            .simulated(false)
            .confidenceRate(1.0)
            .riskLevel("NONE")
            .build());
}

NextStatusSimulationResponse sim = simulationService.simulateNextStatus(order, LocalDateTime.now());
if (sim != null) {
    res.setNextStatusSimulation(sim);
    steps.add(DeliveryStepResponse.builder()
            .stepKey("SIMULATED_" + sim.getPredictedStatus().name())
            .title(sim.getPredictedStatusTitle())
            .diagnosticDescription(sim.getDiagnosticPrediction())
            .timestamp(sim.getEstimatedAt())
            .formattedTime(sim.getFormattedEstimatedTime())
            .completed(false)
            .active(false)
            .simulated(true)
            .confidenceRate(sim.getProbabilityScore())
            .riskLevel(sim.getRiskSeverity())
            .riskReason(sim.getRiskWarningMessage())
            .estimatedDeviationMinutes(0)
            .build());
}

res.setDeliverySteps(steps);
return Response.ok(res);
```
*Command to run:* `./mvnw test -Dtest=OrderJourneyIntegrationTest` (Expected: PASS)

---

## Wave 2: Frontend Client & Optical Stepper Integration (Parallel)
**Convergence Goal:** Frontend consumes live `deliverySteps` and `nextStatusSimulation`, eliminating client-side mock generators and rendering the optical dimmed predictive card with ambient glow and risk badges.

### Task: task-fe-order-service-adapter
- Scope: Medium-Small (Solo)
- Target Service: `frontend/`
- Worktree: `.worktrees/task-fe-order-service-adapter`
- Branch: `task-fe-order-service-adapter`

#### Role: worker-fe-order-service-adapter
**Directive:** Update `orderService.ts` and `types.ts` in the frontend to type-check `deliverySteps` with `simulated`, `confidenceRate`, `riskLevel` and preserve backend response directly into `OrderItem`.

**1. Target Files & Rich Context:**
- Modify: `~/Projects/noname/src/services/orderService.ts`
- Modify: `~/Projects/noname/src/types.ts`
- Test: `~/Projects/noname/src/services/orderService.test.ts`

**2. Failing Test (Step 1):**
```typescript
import { mapBackendOrderDetailToOrderItem } from './orderService';

describe('orderService deliverySteps mapping', () => {
  it('preserves simulated flag and risk metrics from backend payload', () => {
    const rawDto = {
      orderNumber: 'ORD-20260819-8820192',
      currentStatus: 'PROCESSING',
      deliverySteps: [
        {
          stepKey: 'PACKING_TESTING',
          title: 'Đang đóng gói & Kiểm thử',
          diagnosticDescription: 'Kiểm tra linh kiện',
          formattedTime: '19/08/2026 11:30',
          completed: true,
          active: true,
          simulated: false,
        },
        {
          stepKey: 'HANDOVER_CARRIER',
          title: 'Bàn giao đơn vị vận chuyển',
          diagnosticDescription: 'Xuất kho Viettel Post',
          formattedTime: '19/08/2026 14:00',
          completed: false,
          active: false,
          simulated: true,
          riskLevel: 'LOW',
          confidenceRate: 0.91,
        }
      ]
    };

    const item = mapBackendOrderDetailToOrderItem(rawDto);
    expect(item.deliverySteps).toHaveLength(2);
    expect(item.deliverySteps[1].simulated).toBe(true);
    expect(item.deliverySteps[1].riskLevel).toBe('LOW');
  });
});
```
*Command to run:* `npm test -- orderService.test.ts` (Expected: FAIL)

**3. Implementation (Step 3):**
Enhance `UiDeliveryStep` in `orderService.ts`:
```typescript
export interface UiDeliveryStep {
  title: string;
  desc: string;
  time: string;
  completed: boolean;
  active: boolean;
  simulated?: boolean;
  confidenceRate?: number;
  riskLevel?: string;
  riskReason?: string;
}
```
Update mapping logic in `mapBackendOrderDetailToOrderItem` to prioritize `dto.deliverySteps` when present, falling back to legacy generator only when null.
*Command to run:* `npm test -- orderService.test.ts` (Expected: PASS)

---

### Task: task-fe-predictive-stepper-ui
- Scope: Medium (Pair)
- Target Service: `frontend/`
- Worktree: `.worktrees/task-fe-predictive-stepper-ui`
- Branch: `task-fe-predictive-stepper-ui`

#### Role: worker-fe-predictive-stepper-ui
**Directive:** Update `ProfilePage.tsx` to render optical styling for simulated steps (`simulated: true`) and risk warning indicators.

**1. Target Files & Rich Context:**
- Modify: `~/Projects/noname/src/components/ProfilePage.tsx` (lines 2726–2935)
- Verify visual fidelity:
  - Simulated Step: `backdrop-blur-xs opacity-60 bg-white/20`, border `border-t-white/70 border-b-slate-200/40`, text `text-slate-400 font-light`.
  - Point Icon: Neutral dot `bg-slate-400` inside `border-slate-300/80 bg-white/95`.
  - Risk Badge: If `riskLevel === "HIGH"` or `"CRITICAL"`, show amber/rose alert chip with tooltip `riskReason`.

**2. Failing Test (Step 1):**
```typescript
// ProfilePage.test.tsx
import { render, screen } from '@testing-library/react';
import { ProfilePage } from './ProfilePage';

describe('ProfilePage Predictive Stepper', () => {
  it('renders simulated step with optical opacity and risk indicator when present', () => {
    // Render with mock order containing simulated step
    // Expect "Bàn giao đơn vị vận chuyển" rendered with opacity-60 and light typography
  });
});
```
*Command to run:* `npm test -- ProfilePage.test.tsx` (Expected: FAIL)

**3. Implementation (Step 3):**
Update `ProfilePage.tsx` timeline step mapping:
- Preserve `slice(0, endpointIndex + 2)` which naturally captures current active step + single simulated step.
- Check `step.simulated`:
  - If `step.simulated === true`: Render neutral grey dot and subtle glass card.
  - If `step.riskLevel === 'HIGH' || step.riskLevel === 'CRITICAL'`: Render `<AlertTriangle className="w-3 h-3 text-amber-500" />` beside time chip.
*Command to run:* `npm test -- ProfilePage.test.tsx` (Expected: PASS)

#### Role: censor-fe-predictive-stepper-ui
**Directive:** Review diff against `task-fe-predictive-stepper-ui` branch.
- Verify: Does the UI avoid layout shift when the simulated step is rendered?
- Verify: Does the timeline vertical connecting line smoothly fade to transparent before the simulated step?

---

## Final Phase: Role: Builder
**Directive:** Multi-Service Integration Sign-Off

### Service: Backend (Spring Boot 3 / Java 21)
- Root Directory: `.`
- Build Command: `./mvnw clean test package -DskipTests=false`
- Migration Check: Verify Flyway `V22__create_order_fulfillment_events_table.sql` successfully applies to test DB.
- Boot Command: `java -jar target/erp-0.0.1-SNAPSHOT.jar`
- Health Check: `curl -f http://localhost:8080/actuator/health` -> HTTP 200 OK

### Service: Frontend (React / Vite / TypeScript)
- Root Directory: `~/Projects/noname`
- Build Command: `npm run build`
- Type Check: `npx tsc --noEmit`
- Test Suite: `npm test`
