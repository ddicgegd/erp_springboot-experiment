package com.ddicg.erp.modules.order.dto.request;

import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateOrderRequest {
    @NotNull(message = "Danh sách sản phẩm không được rỗng")
    @NotEmpty(message = "Danh sách sản phẩm không được rỗng")
    @Valid
    List<OrderItemRequest> items;

    boolean isFromCart;

    @NotBlank(message = "Mã địa chỉ giao hàng không được để trống")
    String addressId;
    String discountCode;
    String customerNotes;
    String shippingMethod;

    @NotNull(message = "Phương thức thanh toán không được rỗng")
    PaymentMethod paymentMethod;

    String language;
    String bankCode;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class OrderItemRequest {
        @NotNull(message = "SKU sản phẩm không được để trống")
        String attributesSku;

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải lớn hơn 0")
        Integer quantity;
    }
}
