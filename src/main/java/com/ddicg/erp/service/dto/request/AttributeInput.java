package com.ddicg.erp.service.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import com.ddicg.erp.model.embedded.VariantOption;
import com.ddicg.erp.model.embedded.Promotion;
import com.ddicg.erp.model.embedded.SpecificationGroup;
import com.ddicg.erp.model.enums.StockStatus;

@Data
public class AttributeInput {
    private String name;
    private String value;
    private BigDecimal price;
    private BigDecimal salePrice;
    private List<VariantOption> variantOptions;
    private List<Promotion> promotions;
    private List<SpecificationGroup> specifications;
    private StockStatus statusProduct;
}
