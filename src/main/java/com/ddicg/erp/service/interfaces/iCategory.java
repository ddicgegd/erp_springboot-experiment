package com.ddicg.erp.service.interfaces;

import com.ddicg.erp.service.dto.CategoryDto;
import com.ddicg.erp.service.dto.request.CategorySearchRequest;
import com.ddicg.erp.service.dto.request.UpdateCategoryRequest;
import com.ddicg.erp.service.dto.response.CategoryExitingResponse;
import com.ddicg.erp.service.dto.response.ResponseConfig.Response;
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
