package com.ddicg.erp.service.dto.request;

import lombok.Value;

@Value
public class CartItemRequest {
    String sku;
    int quantity;
}
