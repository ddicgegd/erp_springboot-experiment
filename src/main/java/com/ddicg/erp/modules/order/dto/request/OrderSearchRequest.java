package com.ddicg.erp.modules.order.dto.request;

import com.ddicg.erp.core.common.annotation.NormalizedId;
import com.ddicg.erp.core.common.model.enums.OrderStatus;
import java.time.LocalDateTime;

public class OrderSearchRequest {

    private String orderNumber;

    @NormalizedId
    private String customerId;

    private String customerName;
    private String customerEmail;
    private String customerPhone;

    private OrderStatus orderStatus;
    private java.util.List<OrderStatus> orderStatuses;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private Double minAmount;
    private Double maxAmount;

    private Integer page;
    private Integer size;

    private String sortBy;
    private String sortDirection;

    public OrderSearchRequest() {}

    public OrderSearchRequest(String orderNumber, String customerId, String customerName, String customerEmail, String customerPhone, OrderStatus orderStatus, LocalDateTime startDate, LocalDateTime endDate, Double minAmount, Double maxAmount, Integer page, Integer size, String sortBy, String sortDirection) {
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.orderStatus = orderStatus;
        this.startDate = startDate;
        this.endDate = endDate;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.page = page;
        this.size = size;
        this.sortBy = sortBy;
        this.sortDirection = sortDirection;
    }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public OrderStatus getOrderStatus() { return orderStatus; }
    public void setOrderStatus(OrderStatus orderStatus) { this.orderStatus = orderStatus; }
    public java.util.List<OrderStatus> getOrderStatuses() { return orderStatuses; }
    public void setOrderStatuses(java.util.List<OrderStatus> orderStatuses) { this.orderStatuses = orderStatuses; }
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
    public Double getMinAmount() { return minAmount; }
    public void setMinAmount(Double minAmount) { this.minAmount = minAmount; }
    public Double getMaxAmount() { return maxAmount; }
    public void setMaxAmount(Double maxAmount) { this.maxAmount = maxAmount; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }

    public static OrderSearchRequestBuilder builder() { return new OrderSearchRequestBuilder(); }

    public static class OrderSearchRequestBuilder {
        private String orderNumber;
        private String customerId;
        private String customerName;
        private String customerEmail;
        private String customerPhone;
        private OrderStatus orderStatus;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private Double minAmount;
        private Double maxAmount;
        private Integer page;
        private Integer size;
        private String sortBy;
        private String sortDirection;

        OrderSearchRequestBuilder() {}

        public OrderSearchRequestBuilder orderNumber(String orderNumber) { this.orderNumber = orderNumber; return this; }
        public OrderSearchRequestBuilder customerId(String customerId) { this.customerId = customerId; return this; }
        public OrderSearchRequestBuilder customerName(String customerName) { this.customerName = customerName; return this; }
        public OrderSearchRequestBuilder customerEmail(String customerEmail) { this.customerEmail = customerEmail; return this; }
        public OrderSearchRequestBuilder customerPhone(String customerPhone) { this.customerPhone = customerPhone; return this; }
        public OrderSearchRequestBuilder orderStatus(OrderStatus orderStatus) { this.orderStatus = orderStatus; return this; }
        public OrderSearchRequestBuilder startDate(LocalDateTime startDate) { this.startDate = startDate; return this; }
        public OrderSearchRequestBuilder endDate(LocalDateTime endDate) { this.endDate = endDate; return this; }
        public OrderSearchRequestBuilder minAmount(Double minAmount) { this.minAmount = minAmount; return this; }
        public OrderSearchRequestBuilder maxAmount(Double maxAmount) { this.maxAmount = maxAmount; return this; }
        public OrderSearchRequestBuilder page(Integer page) { this.page = page; return this; }
        public OrderSearchRequestBuilder size(Integer size) { this.size = size; return this; }
        public OrderSearchRequestBuilder sortBy(String sortBy) { this.sortBy = sortBy; return this; }
        public OrderSearchRequestBuilder sortDirection(String sortDirection) { this.sortDirection = sortDirection; return this; }

        public OrderSearchRequest build() {
            return new OrderSearchRequest(orderNumber, customerId, customerName, customerEmail, customerPhone, orderStatus, startDate, endDate, minAmount, maxAmount, page, size, sortBy, sortDirection);
        }
    }
}
