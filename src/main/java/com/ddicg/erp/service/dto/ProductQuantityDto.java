package com.ddicg.erp.service.dto;

import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link com.ddicg.erp.model.embedded.ProductQuantity}
 */
@Value
public class ProductQuantityDto implements Serializable {
    String sku;
    int quantity;
}