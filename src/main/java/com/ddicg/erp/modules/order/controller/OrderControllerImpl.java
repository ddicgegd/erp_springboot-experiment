package com.ddicg.erp.modules.order.controller;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.modules.order.dto.response.MyOrderDetailResponse;
import com.ddicg.erp.modules.order.dto.response.MyOrderListResponse;
import com.ddicg.erp.core.common.dto.request.*;
import com.ddicg.erp.core.common.dto.response.PagingResponse;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.modules.iam.dto.request.*;
import com.ddicg.erp.modules.merchandise.dto.request.*;
import com.ddicg.erp.modules.merchandise.mapper.OrderMapper;
import com.ddicg.erp.modules.order.dto.OrderDto;
import com.ddicg.erp.modules.order.dto.request.*;
import com.ddicg.erp.modules.order.service.OrderStatusHandler;
import com.ddicg.erp.modules.order.service.iOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "APIs quản lý đơn hàng")
@SecurityRequirement(name = "bearerAuth")
public class OrderControllerImpl implements OrderController {


    private final iOrder orderService;
    private final OrderMapper orderMapper;
    private final OrderStatusHandler orderStatusHandler;

    /* ==================== Customer Endpoints ==================== */

    @Override
    @Operation(summary = "Tạo đơn hàng mới", description = "Customer tạo đơn hàng mới")
    @PreAuthorize("isAuthenticated()")
    public Response<OrderDto> createOrder(CreateOrderRequest request) {
        log.debug("Yêu cầu REST tạo đơn hàng mới");
        return orderService.createOrder(request);
    }

    @Override
    @Operation(summary = "Lấy danh sách đơn hàng tóm tắt của tôi", description = "Bắt buộc lọc theo trạng thái (?status=PROCESSING), phân trang 1-indexed (mặc định page=1)")
    @PreAuthorize("isAuthenticated()")
    public Response<PagingResponse<MyOrderListResponse>> getMyOrdersList(
            OrderStatus status,
            int page,
            int size,
            String sortBy,
            String sortDirection) {
        log.debug("Yêu cầu REST lấy danh sách đơn hàng của tôi với trạng thái: {}, page: {}, size: {}", status, page, size);
        return orderService.getMyOrdersList(status, page, size, sortBy, sortDirection);
    }

    @Override
    @Operation(summary = "Lấy chi tiết đơn hàng của tôi", description = "Lấy chi tiết đơn hàng theo orderNumber (không bao gồm orderItems)")
    @PreAuthorize("isAuthenticated()")
    public Response<MyOrderDetailResponse> getMyOrderDetail(String orderNumber) {
        log.debug("Yêu cầu REST lấy chi tiết đơn hàng của tôi: {}", orderNumber);
        return orderService.getMyOrderDetail(orderNumber);
    }

    @Override
    @Operation(summary = "Hủy đơn hàng", description = "Customer hoặc Admin hủy đơn hàng")
    @PreAuthorize("isAuthenticated()")
    public Response<OrderDto> cancelOrder(CancelOrderRequest request) {
        log.debug("Yêu cầu REST hủy đơn hàng: {}", request.getOrderId());
        return orderService.cancelOrder(request);
    }

    /* ==================== Admin Endpoints ==================== */

    @Override
    @Operation(summary = "Tìm kiếm đơn hàng", description = "Admin tìm kiếm và lọc đơn hàng")
    @PreAuthorize("isAuthenticated()")
    public Response<PagingResponse<OrderDto>> searchOrders(OrderSearchRequest request) {
        log.debug("Yêu cầu REST tìm kiếm đơn hàng");
        return orderService.searchOrders(request);
    }

    @Override
    @Operation(summary = "Cập nhật thông tin vận chuyển", description = "Admin cập nhật thông tin vận chuyển")
    @PreAuthorize("isAuthenticated()")
    public Response<OrderDto> updateShipping(UpdateShippingRequest request) {
        log.debug("Yêu cầu REST cập nhật thông tin vận chuyển: {}", request.getOrderId());
        return orderService.updateShipping(request);
    }

    @Override
    @Operation(summary = "Cập nhật ngày giao hàng", description = "Admin cập nhật ngày giao hàng")
    @PreAuthorize("isAuthenticated()")
    public Response<OrderDto> updateDelivery(UpdateDeliveryRequest request) {
        log.debug("Yêu cầu REST cập nhật ngày giao hàng: {}", request.getOrderId());
        return orderService.updateDelivery(request);
    }

    @Override
    @Operation(summary = "Cập nhật ghi chú quản trị", description = "Admin cập nhật ghi chú")
    @PreAuthorize("isAuthenticated()")
    public Response<OrderDto> updateAdminNotes(UpdateAdminNotesRequest request) {
        log.debug("Yêu cầu REST cập nhật ghi chú quản trị: {}", request.getOrderId());
        return orderService.updateAdminNotes(request);
    }

    @Override
    @Operation(summary = "Xác nhận đơn hàng", description = "Admin xác nhận đơn hàng")
    @PreAuthorize("isAuthenticated()")
    public Response<OrderDto> confirmOrder(ConfirmOrderRequest request) {
        log.debug("Yêu cầu REST xác nhận đơn hàng: {}", request.getOrderId());
        return orderService.confirmOrder(request);
    }

    @Override
    @Operation(summary = "Hoàn thành đơn hàng", description = "Admin hoàn thành đơn hàng")
    @PreAuthorize("isAuthenticated()")
    public Response<OrderDto> completeOrder(CompleteOrderRequest request) {
        log.debug("Yêu cầu REST hoàn thành đơn hàng: {}", request.getOrderId());
        return orderService.completeOrder(request);
    }

    @Override
    @Operation(summary = "Lấy đơn hàng chờ xử lý", description = "Admin xem danh sách đơn hàng cần xử lý")
    @PreAuthorize("isAuthenticated()")
    public Response<List<OrderDto>> getPendingOrders() {
        log.debug("Yêu cầu REST lấy danh sách đơn hàng chờ xử lý");
        Response<List<OrderDto>> res = orderService.getPendingOrders();
        return res;
    }

    @Override
    @Operation(summary = "Lấy đơn hàng đang giao", description = "Admin xem danh sách đơn hàng đang giao")
    @PreAuthorize("isAuthenticated()")
    public Response<List<OrderDto>> getInProgressOrders() {
        log.debug("Yêu cầu REST lấy danh sách đơn hàng đang giao");
        Response<List<OrderDto>> res = orderService.getInProgressOrders();
        return res;
    }

    @Override
    @Operation(summary = "Thống kê đơn hàng", description = "Admin xem thống kê đơn hàng theo thời gian")
    @PreAuthorize("isAuthenticated()")
    public Response<?> getOrderStatistics(String startDate, String endDate) {
        log.debug("Yêu cầu REST lấy thống kê đơn hàng từ {} đến {}", startDate, endDate);
        return orderService.getOrderStatistics(startDate, endDate);
    }

    /*
     * ==================== Order Status Transitions (Dashboard)
     * ====================
     */

    @Override
    @Operation(summary = "Chuyển trạng thái đơn hàng", description = "Admin chuyển trạng thái (PROCESSING, DELIVERED, READY_FOR_PICKUP, RETURNING, RETURNED, COMPLETED). SHIPPED dùng /ship")
    @PreAuthorize("isAuthenticated()")
    public Response<OrderDto> transitionOrder(TransitionOrderRequest request) {
        log.debug("Yêu cầu REST chuyển trạng thái đơn hàng {} → {}", request.getOrderId(), request.getTargetStatus());
        throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Không hỗ trợ: " + request.getTargetStatus());
    }

    @Override
    @Operation(summary = "Giao hàng", description = "Admin giao đơn cho tài xế → SHIPPED. Tự sinh delivery link + PIN")
    @PreAuthorize("isAuthenticated()")
    public Response<?> shipOrder(TransitionOrderRequest request) {
        log.debug("Yêu cầu REST giao đơn hàng {} cho shipper {}", request.getOrderId(), request.getShipperId());

        String token = UUID.randomUUID().toString();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", request.getOrderId());
        result.put("deliveryToken", token);
        result.put("deliveryUrl", "/api/delivery/" + token);
        result.put("message", "Đã giao đơn cho tài xế. Gửi link cho shipper để bắt đầu giao hàng");

        return Response.ok(result);
    }

    @Override
    @Operation(summary = "Xem PIN shipper", description = "Admin xem mã PIN hiện tại của shipper")
    @PreAuthorize("isAuthenticated()")
    public Response<?> getDeliveryPin(String orderNumber) {
        log.debug("Yêu cầu REST xem PIN giao hàng cho đơn: {}", orderNumber);
        Map<String, Object> pinInfo = new LinkedHashMap<>();
        pinInfo.put("orderNumber", orderNumber);
        pinInfo.put("message", "Tính năng PIN đang phát triển");
        return Response.ok(pinInfo);
    }

    @Override
    @Operation(summary = "Xóa PIN shipper", description = "Admin xóa PIN → shipper mở link lại sẽ thấy tạo PIN mới")
    @PreAuthorize("isAuthenticated()")
    public Response<?> clearDeliveryPin(String orderNumber) {
        log.debug("Yêu cầu REST xóa PIN giao hàng cho đơn: {}", orderNumber);
        return Response.ok(null, "Đã xóa PIN");
    }
}
