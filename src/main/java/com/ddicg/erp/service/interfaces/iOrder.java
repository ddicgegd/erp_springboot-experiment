package com.ddicg.erp.service.interfaces;

import com.ddicg.erp.model.enums.OrderStatus;
import com.ddicg.erp.service.dto.OrderDto;
import com.ddicg.erp.service.dto.request.*;
import com.ddicg.erp.service.dto.response.ResponseConfig.PagingResponse;
import com.ddicg.erp.service.dto.response.ResponseConfig.Response;

import java.util.List;

public interface iOrder {
    Response<OrderDto> createOrder(CreateOrderRequest request);
    Response<OrderDto> getOrderById(String orderId);
    Response<OrderDto> getOrderByOrderNumber(String orderNumber);
    Response<PagingResponse<OrderDto>> getMyOrders(OrderSearchRequest request);
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
