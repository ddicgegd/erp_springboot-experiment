package com.ddicg.erp.modules.order.model;

import com.ddicg.erp.core.common.model.base.BaseEntity;
import com.ddicg.erp.core.common.model.enums.DiscountType;
import com.ddicg.erp.core.common.model.enums.VoucherType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.type.NumericBooleanConverter;

import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers", indexes = {
        @Index(name = "idx_voucher_code", columnList = "code", unique = true),
        @Index(name = "idx_voucher_type", columnList = "voucher_type")
})
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Voucher extends BaseEntity<Long> {

    @Column(name = "code", nullable = false, unique = true, length = 100)
    String code;

    @Column(name = "name", nullable = false, length = 255)
    String name;

    @Column(name = "description", length = 500)
    String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false, length = 50)
    VoucherType voucherType; // SHIPPING hoặc PRODUCT

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 50)
    DiscountType discountType; // PERCENTAGE hoặc FIXED_AMOUNT

    @Column(name = "discount_value", nullable = false)
    Double discountValue; // Ví dụ: 10 (10%) hoặc 20000 (20.000đ)

    @Column(name = "max_discount_amount")
    Double maxDiscountAmount; // Số tiền giảm tối đa nếu là %

    @Column(name = "min_order_amount")
    @Builder.Default
    Double minOrderAmount = 0.0; // Đơn tối thiểu

    @Column(name = "start_date", nullable = false)
    LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    LocalDateTime endDate;

    @Column(name = "applicable_skus", length = 1000)
    String applicableSkus;

    @Column(name = "total_quantity", nullable = false)
    @Builder.Default
    Integer totalQuantity = 0; // Tổng số lượng phát hành

    @Column(name = "used_quantity", nullable = false)
    @Builder.Default
    Integer usedQuantity = 0; // Số lượng đã sử dụng thành công

    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "is_active")
    @Builder.Default
    Boolean isActive = true;

    public boolean isCurrentlyValid() {
        if (Boolean.FALSE.equals(isActive)) return false;
        LocalDateTime now = LocalDateTime.now();
        if (startDate != null && now.isBefore(startDate)) return false;
        if (endDate != null && now.isAfter(endDate)) return false;
        return (totalQuantity != null ? totalQuantity : 0) > (usedQuantity != null ? usedQuantity : 0);
    }
}
