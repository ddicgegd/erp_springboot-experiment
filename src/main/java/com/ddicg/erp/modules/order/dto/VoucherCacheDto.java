package com.ddicg.erp.modules.order.dto;

import com.ddicg.erp.core.common.model.enums.DiscountType;
import com.ddicg.erp.core.common.model.enums.VoucherType;
import com.ddicg.erp.modules.order.model.Voucher;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VoucherCacheDto implements Serializable {
    Long id;
    String code;
    String name;
    VoucherType voucherType;
    DiscountType discountType;
    Double discountValue;
    Double maxDiscountAmount;
    Double minOrderAmount;
    LocalDateTime startDate;
    LocalDateTime endDate;
    Integer totalQuantity;
    Integer usedQuantity;
    Boolean isActive;
    List<String> applicableSkus;

    public static VoucherCacheDto fromEntity(Voucher voucher) {
        if (voucher == null) return null;
        List<String> skus = null;
        if (voucher.getApplicableSkus() != null && !voucher.getApplicableSkus().isBlank()) {
            skus = Arrays.stream(voucher.getApplicableSkus().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        return VoucherCacheDto.builder()
                .id(voucher.getId())
                .code(voucher.getCode())
                .name(voucher.getName())
                .voucherType(voucher.getVoucherType())
                .discountType(voucher.getDiscountType())
                .discountValue(voucher.getDiscountValue())
                .maxDiscountAmount(voucher.getMaxDiscountAmount())
                .minOrderAmount(voucher.getMinOrderAmount())
                .startDate(voucher.getStartDate())
                .endDate(voucher.getEndDate())
                .totalQuantity(voucher.getTotalQuantity())
                .usedQuantity(voucher.getUsedQuantity())
                .isActive(voucher.getIsActive())
                .applicableSkus(skus)
                .build();
    }

    public boolean isCurrentlyValid() {
        if (Boolean.FALSE.equals(isActive)) return false;
        LocalDateTime now = LocalDateTime.now();
        if (startDate != null && now.isBefore(startDate)) return false;
        if (endDate != null && now.isAfter(endDate)) return false;
        return (totalQuantity != null ? totalQuantity : 0) > (usedQuantity != null ? usedQuantity : 0);
    }
}
