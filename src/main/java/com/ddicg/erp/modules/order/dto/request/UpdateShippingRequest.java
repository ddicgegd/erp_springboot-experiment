package com.ddicg.erp.modules.order.dto.request;
import lombok.Data;
@Data
public class UpdateShippingRequest {
    private String orderId;
    private String shippingMethod;
    private String shippingInfo;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getShippingMethod() { return shippingMethod; }
    public void setShippingMethod(String shippingMethod) { this.shippingMethod = shippingMethod; }
    public String getShippingInfo() { return shippingInfo; }
    public void setShippingInfo(String shippingInfo) { this.shippingInfo = shippingInfo; }

}
