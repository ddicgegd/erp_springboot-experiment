package com.ddicg.erp.modules.bookmark.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookmarkDetailResponse {
    String mainSku;
    Integer totalItems;
    Double totalPrice;
    Double totalSalePrice;
    Double totalDiscount;
    Long ttlSecondsRemaining;
    Long expiresAtEpochMs;
    String formattedRemainingTime;
    List<BookmarkItemResponse> items;

    public static BookmarkDetailResponse empty(String mainSku) {
        return BookmarkDetailResponse.builder()
                .mainSku(mainSku)
                .totalItems(0)
                .totalPrice(0.0)
                .totalSalePrice(0.0)
                .totalDiscount(0.0)
                .ttlSecondsRemaining(0L)
                .expiresAtEpochMs(null)
                .formattedRemainingTime("Đã hết hạn")
                .items(Collections.emptyList())
                .build();
    }
}
