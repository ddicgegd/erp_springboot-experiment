package com.ddicg.erp.service.dto.request;

import com.ddicg.erp.common.annotation.NormalizedId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Request DTO để hủy Order.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CancelOrderRequest {

    /**
     * ID của Order cần hủy (bắt buộc).
     * Được normalize tự động: uppercase + remove dashes.
     */
    @NormalizedId
    @NotNull(message = "Order ID không được để trống")
    String orderId;

    /**
     * Lý do hủy đơn hàng (bắt buộc).
     */
    @NotBlank(message = "Lý do hủy không được để trống")
    String cancellationReason;
}
