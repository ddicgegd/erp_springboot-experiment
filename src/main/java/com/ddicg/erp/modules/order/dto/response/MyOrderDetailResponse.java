package com.ddicg.erp.modules.order.dto.response;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.core.common.model.enums.ShippingMethod;
import com.ddicg.erp.modules.iam.model.Address;
import com.ddicg.erp.modules.merchandise.dto.AuditInfoDto;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MyOrderDetailResponse {

    Long id;

    String orderNumber;

    List<OrderStatus> status;

    OrderStatus currentStatus;

    String currentStatusDescription;

    // Customer info
    Long customerId;

    String customerName;

    String customerEmail;

    String customerPhone;

    String shippingAddress;

    Address shippingInfo;

    // Pricing
    Double subtotal;

    Double discountAmount;

    List<String> discountCodes;

    Double taxAmount;

    Double shippingFee;

    Double totalAmount;

    // Shipping & Delivery
    String shippingMethod;

    LocalDateTime estimatedDeliveryDate;

    LocalDateTime actualDeliveryDate;

    // Notes & Timestamps
    String customerNotes;

    String cancellationReason;

    LocalDateTime cancelledAt;

    LocalDateTime confirmedAt;

    LocalDateTime completedAt;

    LocalDateTime createdAt;

    AuditInfoDto auditInfo;
}
