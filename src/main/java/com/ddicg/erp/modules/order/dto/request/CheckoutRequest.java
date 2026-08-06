package com.ddicg.erp.modules.order.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public class CheckoutRequest {

    @NotEmpty
    private List<CheckoutItem> items;

    public CheckoutRequest() {}
    public CheckoutRequest(List<CheckoutItem> items) { this.items = items; }

    public List<CheckoutItem> getItems() { return items; }
    public void setItems(List<CheckoutItem> items) { this.items = items; }

    public static class CheckoutItem {
        @NotNull
        private String sku;
        @NotNull
        @Positive
        private Integer quantity;

        public CheckoutItem() {}
        public CheckoutItem(String sku, Integer quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
