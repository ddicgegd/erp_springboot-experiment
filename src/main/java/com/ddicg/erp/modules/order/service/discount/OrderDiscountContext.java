package com.ddicg.erp.modules.order.service.discount;

import com.ddicg.erp.core.common.model.enums.ShippingMethod;
import com.ddicg.erp.modules.order.dto.request.CreateOrderRequest;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderDiscountContext implements Serializable {
    Double subtotal;
    Double rawShippingFee;
    List<String> discountCodes;
    String customerId;
    ShippingMethod shippingMethod;
    List<CreateOrderRequest.OrderItemRequest> items;
    List<com.ddicg.erp.modules.merchandise.model.Attributes> attributesList;
}
