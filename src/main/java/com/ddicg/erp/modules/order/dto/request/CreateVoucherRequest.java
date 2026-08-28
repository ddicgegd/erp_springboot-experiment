package com.ddicg.erp.modules.order.dto.request;

import com.ddicg.erp.core.common.model.enums.DiscountType;
import com.ddicg.erp.core.common.model.enums.VoucherType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateVoucherRequest {

    @NotBlank(message = "Mã voucher không được để trống")
    String code;

    @NotBlank(message = "Tên voucher không được để trống")
    String name;

    String description;

    @NotNull(message = "Loại voucher không được để trống (GLOBAL_ORDER, PRODUCT_ITEM hoặc SHIPPING)")
    VoucherType voucherType;

    @NotNull(message = "Kiểu giảm giá không được để trống (PERCENTAGE hoặc FIXED_AMOUNT)")
    DiscountType discountType;

    @NotNull(message = "Giá trị giảm không được để trống")
    @Min(value = 0, message = "Giá trị giảm phải lớn hơn hoặc bằng 0")
    Double discountValue;

    Double maxDiscountAmount;

    Double minOrderAmount;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    LocalDateTime startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    LocalDateTime endDate;

    @NotNull(message = "Tổng số lượng không được để trống")
    @Min(value = 1, message = "Tổng số lượng phát hành phải từ 1 trở lên")
    Integer totalQuantity;

    java.util.List<String> applicableSkus;
}
