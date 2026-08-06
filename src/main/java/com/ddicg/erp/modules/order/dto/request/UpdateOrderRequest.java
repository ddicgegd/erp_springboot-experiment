package com.ddicg.erp.modules.order.dto.request;

import com.ddicg.erp.core.common.annotation.NormalizedId;
import com.ddicg.erp.modules.iam.model.Address;
import com.ddicg.erp.core.common.model.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Request DTO để cập nhật thông tin Order.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateOrderRequest {

    /**
     * ID của Order cần cập nhật (bắt buộc).
     * Được normalize tự động: uppercase + remove dashes.
     */
    @NormalizedId
    @NotNull(message = "Order ID không được để trống")
    String orderId;

    /**
     * Trạng thái mới của Order (optional).
     */
    OrderStatus status;

    /**
     * Thông tin giao hàng mới (optional).
     */
    Address shippingInfo;

    /**
     * Ghi chú của admin (optional).
     */
    String adminNotes;

    /**
     * Mã vận đơn theo dõi (optional).
     */
    String trackingNumber;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

}
