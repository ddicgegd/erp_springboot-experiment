package com.ddicg.erp.service.interfaces;

import com.ddicg.erp.service.dto.ProductDto;

import java.util.List;

public interface iProductCaching {
    void addProduct(List<ProductDto> items);
}
