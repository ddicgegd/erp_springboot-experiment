package com.ddicg.erp.modules.order.dto.response;

import com.ddicg.erp.core.common.model.enums.DiscountType;
import com.ddicg.erp.core.common.model.enums.VoucherType;
import com.ddicg.erp.modules.order.model.Voucher;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VoucherResponseDto {
    Long id;
    String code;
    String name;
    String description;
    VoucherType voucherType;
    DiscountType discountType;
    Double discountValue;
    Double maxDiscountAmount;
    Double minOrderAmount;
    LocalDateTime startDate;
    LocalDateTime endDate;
    Integer totalQuantity;
    Integer usedQuantity;
    Integer availableQuantity;
    Boolean isActive;
    Boolean isValid;
    java.util.List<String> applicableSkus;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public static VoucherResponseDto fromEntity(Voucher voucher) {
        if (voucher == null) return null;
        int total = voucher.getTotalQuantity() != null ? voucher.getTotalQuantity() : 0;
        int used = voucher.getUsedQuantity() != null ? voucher.getUsedQuantity() : 0;
        int available = Math.max(0, total - used);

        java.util.List<String> skus = null;
        if (voucher.getApplicableSkus() != null && !voucher.getApplicableSkus().isBlank()) {
            skus = java.util.Arrays.stream(voucher.getApplicableSkus().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        }

        return VoucherResponseDto.builder()
                .id(voucher.getId())
                .code(voucher.getCode())
                .name(voucher.getName())
                .description(voucher.getDescription())
                .voucherType(voucher.getVoucherType())
                .discountType(voucher.getDiscountType())
                .discountValue(voucher.getDiscountValue())
                .maxDiscountAmount(voucher.getMaxDiscountAmount())
                .minOrderAmount(voucher.getMinOrderAmount())
                .startDate(voucher.getStartDate())
                .endDate(voucher.getEndDate())
                .totalQuantity(total)
                .usedQuantity(used)
                .availableQuantity(available)
                .isActive(voucher.getIsActive())
                .isValid(voucher.isCurrentlyValid())
                .applicableSkus(skus)
                .createdAt(voucher.getCreatedAt())
                .updatedAt(voucher.getUpdatedAt())
                .build();
    }
}
