package com.ddicg.erp.service.dto.request;
import lombok.Data;
@Data
public class UpdateShippingRequest {
    private String orderId;
    private String shippingMethod;
    private String shippingInfo;
}
