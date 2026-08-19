package com.ddicg.erp.modules.order.dto.request;

import com.ddicg.erp.core.common.model.enums.DiscountType;
import com.ddicg.erp.core.common.model.enums.VoucherType;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateVoucherRequest {

    String name;
    String description;
    VoucherType voucherType;
    DiscountType discountType;

    @Min(value = 0, message = "Giá trị giảm phải lớn hơn hoặc bằng 0")
    Double discountValue;

    Double maxDiscountAmount;
    Double minOrderAmount;
    LocalDateTime startDate;
    LocalDateTime endDate;

    @Min(value = 1, message = "Tổng số lượng phát hành phải từ 1 trở lên")
    Integer totalQuantity;

    Boolean isActive;
    java.util.List<String> applicableSkus;
}
