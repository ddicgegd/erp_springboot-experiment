package com.ddicg.erp.modules.merchandise.dto.request;

import jakarta.validation.constraints.NotBlank;

public class CreateCategoryRequest {
    @NotBlank(message = "Tên danh mục không được để trống")
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
