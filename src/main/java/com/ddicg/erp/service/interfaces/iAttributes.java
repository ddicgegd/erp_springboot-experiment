package com.ddicg.erp.service.interfaces;

import com.ddicg.erp.service.dto.AttributesDto;
import com.ddicg.erp.service.dto.request.CreateAttributesRequest;
import com.ddicg.erp.service.dto.request.UpdateAttributesRequest;
import com.ddicg.erp.service.dto.response.ResponseConfig.Response;
import lombok.NonNull;

import java.util.List;
import org.springframework.data.domain.Page;
import com.ddicg.erp.service.dto.request.AttributesSearchRequest;

public interface iAttributes {

    Response<List<AttributesDto>> create(@NonNull CreateAttributesRequest request);

    Response<?> update(@NonNull UpdateAttributesRequest request);

    Response<?> delete(@NonNull List<String> skus);

    Response<?> deleteByProduct(@NonNull String productId);

    Page<AttributesDto> search(@NonNull AttributesSearchRequest request);

    List<AttributesDto> getAttributesByProductId(String productId);

    Response<List<AttributesDto>> getAttributesByIds(List<Long> ids);

    List<Long> searchAttributesIds(AttributesSearchRequest request);

    Response<List<AttributesDto>> getAttributesBySkus(List<String> skus);

    Response<List<AttributesDto>> getAttributesByProductSkus(List<String> productSkus);
}
