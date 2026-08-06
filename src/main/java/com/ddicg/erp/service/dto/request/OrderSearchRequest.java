package com.ddicg.erp.service.dto.request;

import com.ddicg.erp.common.annotation.NormalizedId;
import com.ddicg.erp.model.enums.OrderStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

/**
 * Request DTO để tìm kiếm Orders với các điều kiện filter.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderSearchRequest {

    /**
     * Mã đơn hàng (VD: ORD-2024-001).
     */
    String orderNumber;

    /**
     * ID của khách hàng.
     * Được normalize tự động: uppercase + remove dashes.
     */
    @NormalizedId
    String customerId;

    /**
     * Tên khách hàng (tìm kiếm gần đúng).
     */
    String customerName;

    /**
     * Email khách hàng.
     */
    String customerEmail;

    /**
     * Số điện thoại khách hàng.
     */
    String customerPhone;

    /**
     * Trạng thái đơn hàng.
     */
    OrderStatus orderStatus;

    /**
     * Ngày bắt đầu khoảng thời gian tìm kiếm.
     */
    LocalDateTime startDate;

    /**
     * Ngày kết thúc khoảng thời gian tìm kiếm.
     */
    LocalDateTime endDate;

    /**
     * Tổng tiền tối thiểu.
     */
    Double minAmount;

    /**
     * Tổng tiền tối đa.
     */
    Double maxAmount;

    /**
     * Số trang (bắt đầu từ 0).
     */
    Integer page;

    /**
     * Số lượng items mỗi trang.
     */
    Integer size;

    /**
     * Trường để sắp xếp (auditInfo.createdAt, totalAmount, status).
     */
    String sortBy;

    /**
     * Hướng sắp xếp (ASC, DESC).
     */
    String sortDirection;
}
