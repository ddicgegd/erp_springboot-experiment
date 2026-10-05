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
