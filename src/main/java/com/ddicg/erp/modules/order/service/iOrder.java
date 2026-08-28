package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.modules.order.dto.OrderDto;
import com.ddicg.erp.modules.iam.dto.request.*;
import com.ddicg.erp.modules.merchandise.dto.request.*;
import com.ddicg.erp.modules.order.dto.request.*;
import com.ddicg.erp.modules.order.dto.response.MyOrderDetailResponse;
import com.ddicg.erp.modules.order.dto.response.MyOrderListResponse;
import com.ddicg.erp.core.common.dto.response.PagingResponse;
import com.ddicg.erp.core.common.dto.response.Response;

import java.util.List;

public interface iOrder {
    Response<OrderDto> createOrder(CreateOrderRequest request);
    Response<PagingResponse<MyOrderListResponse>> getMyOrdersList(OrderStatus status, int page, int size, String sortBy, String sortDirection);
    Response<MyOrderDetailResponse> getMyOrderDetail(String orderNumber);
    Response<PagingResponse<OrderDto>> searchOrders(OrderSearchRequest request);
    Response<List<OrderDto>> getPendingOrders();
    Response<List<OrderDto>> getInProgressOrders();
    Response<?> getOrderStatistics(String startDate, String endDate);
    Response<OrderDto> updateShipping(UpdateShippingRequest request);
    Response<OrderDto> updateDelivery(UpdateDeliveryRequest request);
    Response<OrderDto> updateAdminNotes(UpdateAdminNotesRequest request);
    Response<OrderDto> confirmOrder(ConfirmOrderRequest request);
    Response<OrderDto> cancelOrder(CancelOrderRequest request);
    Response<OrderDto> completeOrder(CompleteOrderRequest request);
    Response<OrderDto> processOrder(ProcessOrderRequest request);
    Response<OrderDto> shipOrder(ShipOrderRequest request);
    Response<OrderDto> markDelayed(DelayOrderRequest request);
    Response<OrderDto> deliverOrder(DeliverOrderRequest request);
    Response<OrderDto> readyForPickup(ReadyForPickupRequest request);
    Response<OrderDto> pickupOrder(PickupOrderRequest request);
    Response<OrderDto> returnOrder(ReturnOrderRequest request);
    Response<OrderDto> confirmReturn(ConfirmReturnRequest request);
    Response<OrderDto> refundOrder(RefundOrderRequest request);
    Response<OrderDto> processPayment(PaymentCallbackRequest request);
    void setStatus(String orderNumber, OrderStatus status);
}
