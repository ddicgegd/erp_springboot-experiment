package com.ddicg.erp.modules.merchandise.mapper;

import com.ddicg.erp.modules.merchandise.model.Product;
import com.ddicg.erp.modules.merchandise.dto.ProductDto;
import com.ddicg.erp.modules.merchandise.dto.request.UpdateProductRequest;
import org.mapstruct.*;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), 
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {CategoryMapper.class}
)
public interface ProductMapper extends EntityMapper<ProductDto, Product> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Product partialUpdate(ProductDto productDto, @MappingTarget Product product);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "skuInfo", ignore = true)
    @Mapping(target = "category", ignore = true)
    void updateFromRequest(UpdateProductRequest request, @MappingTarget Product product);
    //sda
}