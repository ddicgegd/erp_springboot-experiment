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
public class MyOrderListResponse {

    String orderNumber;

    List<String> productNames;

    LocalDateTime orderDate;

    LocalDateTime createdAt;

    Double totalAmount;

    OrderStatus currentStatus;

    String currentStatusDescription;
}
