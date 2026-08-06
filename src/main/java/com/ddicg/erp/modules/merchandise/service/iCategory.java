package com.ddicg.erp.modules.merchandise.service;

import com.ddicg.erp.modules.merchandise.dto.CategoryDto;
import com.ddicg.erp.modules.merchandise.dto.request.CategorySearchRequest;
import com.ddicg.erp.modules.merchandise.dto.request.UpdateCategoryRequest;
import com.ddicg.erp.modules.merchandise.dto.response.CategoryExitingResponse;
import com.ddicg.erp.core.common.dto.response.Response;
import lombok.NonNull;
import org.springframework.data.domain.Page;

import java.util.List;

public interface iCategory {
    Response<?> create(String name);
    Response<?> update(final UpdateCategoryRequest request);
    Response<?> delete(@NonNull final List<String> ids);
    Page<CategoryDto> search(@NonNull final CategorySearchRequest request);
    CategoryExitingResponse isExiting(String name);

    Response<List<CategoryDto>> getCategoriesByIds(List<Long> ids);
    Response<List<CategoryDto>> getCategoriesBySkus(List<String> skus);
}
