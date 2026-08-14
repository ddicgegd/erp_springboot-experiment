package com.ddicg.erp.modules.merchandise.service;

import com.ddicg.erp.modules.merchandise.dto.request.GetProductRequest;
import com.ddicg.erp.modules.merchandise.dto.ProductDto;
import com.ddicg.erp.modules.merchandise.dto.request.CreateProductRequest;
import com.ddicg.erp.modules.merchandise.dto.request.UpdateProductRequest;
import com.ddicg.erp.modules.merchandise.dto.response.ProductIsExiting;
import com.ddicg.erp.core.common.dto.response.Response;
import lombok.NonNull;
import org.springframework.data.domain.Page;

import java.util.List;


public interface iProduct {
    Response<?> addProduct(CreateProductRequest request);
    Response<?> updateProduct(UpdateProductRequest request);
    Response<?> delete(List<String> skus);
    Page<ProductDto> searchProducts(@NonNull final GetProductRequest request);
    ProductIsExiting isExiting(String name);
    void viewCount(String sku);
    void totalSoldQuantity(String sku);
    void totalRevenue(String sku, double price);

    Response<List<ProductDto>> getProductsByIds(List<Long> ids);
    Response<List<ProductDto>> getProductsBySkus(List<String> skus);
    Response<List<ProductDto>> getProductsByCategorySkus(List<String> categorySkus);
}
