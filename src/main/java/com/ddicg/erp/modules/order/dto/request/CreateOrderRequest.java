package com.ddicg.erp.modules.order.dto.request;

import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import java.util.List;

public class CreateOrderRequest {
    private List<OrderItemRequest> items;
    private boolean isFromCart;
    private String addressId;
    private String discountCode;
    private String customerNotes;
    private String shippingMethod;
    private PaymentMethod paymentMethod;
    private String language;
    private String bankCode;

    public CreateOrderRequest() {}

    public List<OrderItemRequest> getItems() { return items; }
    public void setItems(List<OrderItemRequest> items) { this.items = items; }
    public boolean isFromCart() { return isFromCart; }
    public void setFromCart(boolean isFromCart) { this.isFromCart = isFromCart; }
    public String getAddressId() { return addressId; }
    public void setAddressId(String addressId) { this.addressId = addressId; }
    public String getDiscountCode() { return discountCode; }
    public void setDiscountCode(String discountCode) { this.discountCode = discountCode; }
    public String getCustomerNotes() { return customerNotes; }
    public void setCustomerNotes(String customerNotes) { this.customerNotes = customerNotes; }
    public String getShippingMethod() { return shippingMethod; }
    public void setShippingMethod(String shippingMethod) { this.shippingMethod = shippingMethod; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }

    public static class OrderItemRequest {
        private String attributesSku;
        private Integer quantity;

        public OrderItemRequest() {}
        public OrderItemRequest(String attributesSku, Integer quantity) {
            this.attributesSku = attributesSku;
            this.quantity = quantity;
        }

        public String getAttributesSku() { return attributesSku; }
        public void setAttributesSku(String attributesSku) { this.attributesSku = attributesSku; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
