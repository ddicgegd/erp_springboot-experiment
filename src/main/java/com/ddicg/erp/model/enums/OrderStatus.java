package com.ddicg.erp.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    PENDING("Chờ xác nhận", "Đơn hàng đã được tạo và đang chờ được xác nhận"),
    WAITING_PAYMENT("Chờ thanh toán", "Đơn hàng đang chờ thanh toán trực tuyến từ khách hàng"),
    CONFIRMED("Đã xác nhận", "Đơn hàng đã được xác nhận và đang chờ xử lý"),
    PROCESSING("Đang xử lý", "Đơn hàng đang được đóng gói và chuẩn bị giao"),
    SHIPPING("Đang giao hàng", "Đơn hàng đang được shipper vận chuyển đến khách"),
    READY_FOR_PICKUP("Chờ lấy hàng", "Đơn hàng đã sẵn sàng để khách đến lấy tại cửa hàng"),
    DELAYED("Giao hàng chậm", "Đơn hàng đang bị chậm so với thời gian dự kiến giao"),
    DELIVERED("Đã giao hàng", "Đơn hàng đã được giao thành công đến khách hàng"),
    COMPLETED("Hoàn thành", "Đơn hàng đã hoàn tất và khách hàng đã nhận được hàng"),
    FAILED("Thanh toán thất bại", "Giao dịch thanh toán trực tuyến của đơn hàng bị thất bại"),
    CANCELLED("Đã hủy", "Đơn hàng đã bị hủy và không thể tiếp tục xử lý"),
    RETURNING("Hoàn trả hàng", "Đơn hàng đang trong quá trình được khách hàng hoàn trả"),
    RETURNED("Đã trả hàng", "Đơn hàng đã được khách hàng hoàn trả lại thành công"),
    REFUNDED("Đã hoàn tiền", "Số tiền của đơn hàng đã được hoàn trả lại cho khách hàng");

    private final String displayName;
    private final String description;
}
