package com.ddicg.erp.modules.order.dto.request;

import com.ddicg.erp.core.common.model.enums.ShippingMethod;
import lombok.Data;

@Data
public class UpdateShippingRequest {
    private String orderId;
    private ShippingMethod shippingMethod;
    private String shippingInfo;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public ShippingMethod getShippingMethod() { return shippingMethod; }
    public void setShippingMethod(ShippingMethod shippingMethod) { this.shippingMethod = shippingMethod; }
    public String getShippingInfo() { return shippingInfo; }
    public void setShippingInfo(String shippingInfo) { this.shippingInfo = shippingInfo; }

}
