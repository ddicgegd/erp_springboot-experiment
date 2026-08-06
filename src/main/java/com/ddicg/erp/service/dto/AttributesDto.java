package com.ddicg.erp.service.dto;

import com.ddicg.erp.model.entity.Attributes;
import com.ddicg.erp.model.enums.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Set;


/**
 * DTO for {@link Attributes}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttributesDto implements Serializable {

    Long id;

    String name;

    SkuInfoDto sku;

    double price;

    double salePrice;

    List<VariantOptionDto> variantOptions;

    /** Trang thai ton kho (IN_STOCK, OUT_OF_STOCK, LOW_STOCK) */
    StockStatus statusProduct;

    List<SpecificationGroupDto> specifications;

    List<PromotionDto> promotions;

    Set<String> keywords;

    /** Thong tin kiem toan */
    AuditInfoDto auditInfo;

    /** Thong tin san pham cha */
    ProductDto product;
}
