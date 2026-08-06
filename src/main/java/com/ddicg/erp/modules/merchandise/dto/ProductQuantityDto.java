package com.ddicg.erp.modules.merchandise.dto;

import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link com.ddicg.erp.core.common.model.embedded.ProductQuantity}
 */
@Value
public class ProductQuantityDto implements Serializable {
    String sku;
    int quantity;
}