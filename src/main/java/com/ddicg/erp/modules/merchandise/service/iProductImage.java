package com.ddicg.erp.modules.merchandise.service;

import com.ddicg.erp.core.common.dto.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface iProductImage {
    Response<?> addProductImages(String productId, List<MultipartFile> images);
    Response<?> deleteProductImage(String productId, String imageKey);
    Response<?> replaceProductImages(String productId, List<MultipartFile> images);
    byte[] getProductImage(String imageName);
}
