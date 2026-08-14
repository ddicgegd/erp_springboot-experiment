package com.ddicg.erp.modules.order.dto;

import com.ddicg.erp.modules.merchandise.dto.AuditInfoDto;

import com.ddicg.erp.modules.iam.model.Address;
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
public class OrderDto {

    Long id;

    String orderNumber;

    List<OrderStatus> status;

    OrderStatus currentStatus;

    String currentStatusDescription;

    // Customer info
    String customerId;

    String customerName;

    String customerEmail;

    String customerPhone;

    String shippingAddress;

    // Order items
    List<OrderItemDto> orderItems;

    // Pricing
    Double subtotal;

    Double discountAmount;

    String discountCode;

    Double taxAmount;

    Double shippingFee;

    Double totalAmount;

    // Shipping
    Address shippingInfo;

    // Notes
    String customerNotes;

    String adminNotes;

    String cancellationReason;

    // Timestamps
    LocalDateTime cancelledAt;

    String cancelledBy;

    LocalDateTime confirmedAt;

    String confirmedBy;

    LocalDateTime completedAt;

    // Related entities
    String shoppingCartId;

    AuditInfoDto auditInfo;
}
