package com.ddicg.erp.modules.order.controller;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.modules.order.dto.OrderDto;
import com.ddicg.erp.modules.order.dto.response.MyOrderDetailResponse;
import com.ddicg.erp.modules.order.dto.response.MyOrderListResponse;
import com.ddicg.erp.modules.iam.dto.request.*;
import com.ddicg.erp.modules.merchandise.dto.request.*;
import com.ddicg.erp.modules.order.dto.request.*;
import com.ddicg.erp.core.common.dto.request.*;
import com.ddicg.erp.core.common.dto.response.PagingResponse;
import com.ddicg.erp.core.common.dto.response.Response;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/orders")
public interface OrderController {

    /* ==================== Customer Order Operations ==================== */

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Response<OrderDto> createOrder(@Valid @RequestBody CreateOrderRequest request);

    @GetMapping("/my-orders/list")
    @ResponseStatus(HttpStatus.OK)
    Response<PagingResponse<MyOrderListResponse>> getMyOrdersList(
            @RequestParam(name = "status", required = true) OrderStatus status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "auditInfo.createdAt") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "DESC") String sortDirection
    );

    @GetMapping("/my-orders/{orderNumber}")
    @ResponseStatus(HttpStatus.OK)
    Response<MyOrderDetailResponse> getMyOrderDetail(@PathVariable String orderNumber);

    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.OK)
    Response<OrderDto> cancelOrder(@Valid @RequestBody CancelOrderRequest request);

    /* ==================== Admin Order Operations ==================== */

    @PostMapping("/search")
    @ResponseStatus(HttpStatus.OK)
    Response<PagingResponse<OrderDto>> searchOrders(@RequestBody OrderSearchRequest request);

    @PutMapping("/shipping")
    @ResponseStatus(HttpStatus.OK)
    Response<OrderDto> updateShipping(@Valid @RequestBody UpdateShippingRequest request);

    @PutMapping("/delivery")
    @ResponseStatus(HttpStatus.OK)
    Response<OrderDto> updateDelivery(@Valid @RequestBody UpdateDeliveryRequest request);

    @PutMapping("/admin-notes")
    @ResponseStatus(HttpStatus.OK)
    Response<OrderDto> updateAdminNotes(@Valid @RequestBody UpdateAdminNotesRequest request);

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.OK)
    Response<OrderDto> confirmOrder(@Valid @RequestBody ConfirmOrderRequest request);

    @PostMapping("/complete")
    @ResponseStatus(HttpStatus.OK)
    Response<OrderDto> completeOrder(@Valid @RequestBody CompleteOrderRequest request);

    @GetMapping("/pending")
    @ResponseStatus(HttpStatus.OK)
    Response<List<OrderDto>> getPendingOrders();

    @GetMapping("/in-progress")
    @ResponseStatus(HttpStatus.OK)
    Response<List<OrderDto>> getInProgressOrders();

    @GetMapping("/statistics")
    @ResponseStatus(HttpStatus.OK)
    Response<?> getOrderStatistics(
            @RequestParam String startDate,
            @RequestParam String endDate);

    /*
     * ==================== Order Status Transitions (Dashboard)
     * ====================
     */

    /**
     * Dashboard Admin chuyển trạng thái đơn hàng.
     * Dùng cho: PROCESSING, DELIVERED, READY_FOR_PICKUP, RETURNING, RETURNED,
     * COMPLETED.
     * SHIPPED phải dùng endpoint riêng (cần thông tin tài xế).
     */
    @PostMapping("/transition")
    @ResponseStatus(HttpStatus.OK)
    Response<OrderDto> transitionOrder(@Valid @RequestBody TransitionOrderRequest request);

    /**
     * Dashboard Admin chuyển sang SHIPPED — bắt buộc có thông tin tài xế.
     * Hệ thống sẽ tự sinh delivery token + PIN và lưu vào Redis.
     */
    @PostMapping("/ship")
    @ResponseStatus(HttpStatus.OK)
    Response<?> shipOrder(@Valid @RequestBody TransitionOrderRequest request);

    /**
     * Admin xem PIN hiện tại của shipper.
     */
    @GetMapping("/delivery-pin/{orderNumber}")
    @ResponseStatus(HttpStatus.OK)
    Response<?> getDeliveryPin(@PathVariable String orderNumber);

    /**
     * Admin xóa PIN → shipper mở link lại sẽ thấy màn hình tạo PIN mới.
     * Dùng khi shipper quên PIN hoặc cần đổi máy.
     */
    @DeleteMapping("/delivery-pin/{orderNumber}")
    @ResponseStatus(HttpStatus.OK)
    Response<?> clearDeliveryPin(@PathVariable String orderNumber);
}
