package com.ddicg.erp.modules.merchandise.service;

import com.ddicg.erp.modules.merchandise.dto.ProductDto;

import java.util.List;

public interface iProductCaching {
    void addProduct(List<ProductDto> items);
}
