package com.ddicg.erp.modules.merchandise.dto.request;

import jakarta.validation.constraints.NotBlank;

public class DeleteProductImageRequest {
    @NotBlank(message = "SKU sản phẩm không được để trống")
    private String sku;
    
    @NotBlank(message = "Image key không được để trống")
    private String imageKey;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getImageKey() { return imageKey; }
    public void setImageKey(String imageKey) { this.imageKey = imageKey; }
}
