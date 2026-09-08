package com.ddicg.erp.modules.bookmark.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookmarkItemResponse {
    String sku;
    String productName;
    String imageUrl;
    String attributesTitle;
    Double unitPrice;
    Double salePrice;
    Integer quantity;
    Double subTotal;
    Boolean isAvailable;
    Integer stock;
}
