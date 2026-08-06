package com.ddicg.erp.service.dto.request;

import com.ddicg.erp.model.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Request DTO để tạo Order mới.
 * Hỗ trợ cả tạo trực tiếp (có items) và tạo từ giỏ hàng (có cartId).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateOrderRequest {

    /**
     * Danh sách sản phẩm trong đơn hàng.
     * Bắt buộc nếu không dùng giỏ hàng (isFromCart = false).
     */
    List<OrderItemRequest> items;

    /**
     * Cờ đánh dấu tạo đơn hàng từ giỏ hàng (optional).
     * Nếu là true, hệ thống sẽ tự động lấy giỏ hàng của user đang đăng nhập.
     */
    @JsonProperty("is_from_cart")
    boolean isFromCart;

    /**
     * Thông tin giao hàng (bắt buộc).
     */
    @JsonProperty("address_id")
    @NotNull(message = "Thông tin giao hàng không được để trống")
    String addressId;

    /**
     * Mã giảm giá (optional).
     */
    @JsonProperty("discount_code")
    String discountCode;

    /**
     * Ghi chú của khách hàng (optional).
     */
    @JsonProperty("customer_notes")
    String customerNotes;

    /**
     * Thông tin thanh toán.
     */
    @JsonProperty("shipping_method")
    String shippingMethod;

    /**
     * Kiểu thanh toán
     */
    @JsonProperty("payment_method")
    PaymentMethod paymentMethod;

    /**
     * Ngôn ngữ thanh toán (vn, en)
     */
    @JsonProperty("language")
    String language;

    /**
     * Mã ngân hàng (nếu có, dùng cho VNPay...)
     */
    @JsonProperty("bank_code")
    String bankCode;

    /**
     * Chi tiết một sản phẩm trong Order.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class OrderItemRequest {

        /**
         * ID của Attributes (variant sản phẩm) - bắt buộc.
         * Được normalize tự động: uppercase + remove dashes.
         */
        @NotNull(message = "Attributes sku không được để trống")
        String attributesSku;

        /**
         * Số lượng đặt mua (bắt buộc).
         */
        @NotNull(message = "Số lượng không được để trống")
        Integer quantity;
    }
}

