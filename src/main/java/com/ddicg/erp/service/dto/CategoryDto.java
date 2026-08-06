package com.ddicg.erp.service.dto;

import com.ddicg.erp.model.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


/**
 * DTO for {@link Category}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryDto implements Serializable {

    Long id;

    String name;

    /** Thong tin SKU */
    SkuInfoDto skuInfo;

    /** Số lượng sản phẩm trong danh mục */
    Long productCount;
}
