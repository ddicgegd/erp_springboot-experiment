package com.ddicg.erp.service.interfaces;

import com.ddicg.erp.service.dto.request.GetProductRequest;
import com.ddicg.erp.service.dto.ProductDto;
import com.ddicg.erp.service.dto.request.CreateProductRequest;
import com.ddicg.erp.service.dto.request.UpdateProductRequest;
import com.ddicg.erp.service.dto.response.ProductIsExiting;
import com.ddicg.erp.service.dto.response.ResponseConfig.Response;
import lombok.NonNull;
import org.springframework.data.domain.Page;

import java.util.List;


public interface iProduct {
    Response<?> addProduct(CreateProductRequest request);
    Response<?> updateProduct(UpdateProductRequest request);
    Response<?> deleteProduct(@NonNull final List<Long> ids);
    Page<ProductDto> searchProducts(@NonNull final GetProductRequest request);
    ProductIsExiting isExiting(String name);
    void viewCount(String productId);
    void totalSoldQuantity(String productId);
    void totalRevenue(String productId, double price);

    Response<List<ProductDto>> getProductsByIds(List<Long> ids);
    Response<List<ProductDto>> getProductsBySkus(List<String> skus);
    Response<List<ProductDto>> getProductsByCategorySkus(List<String> categorySkus);
}
