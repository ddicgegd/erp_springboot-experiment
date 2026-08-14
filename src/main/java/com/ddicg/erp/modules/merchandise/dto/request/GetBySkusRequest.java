package com.ddicg.erp.modules.merchandise.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class GetBySkusRequest {
    @NotEmpty(message = "Danh sách SKU không được để trống")
    private List<String> skus;

    public List<String> getSkus() { return skus; }
    public void setSkus(List<String> skus) { this.skus = skus; }
}
