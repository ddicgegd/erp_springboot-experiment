package com.ddicg.erp.service.dto.request;

import lombok.Data;
import com.ddicg.erp.model.enums.OrderStatus;

@Data
public class TransitionOrderRequest {
    private String orderId;
    private OrderStatus targetStatus;
    private OrderStatus newStatus;
    private String note;
    private String shipperId;
}
