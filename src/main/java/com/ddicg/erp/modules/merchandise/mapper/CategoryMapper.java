package com.ddicg.erp.modules.merchandise.mapper;

import com.ddicg.erp.modules.merchandise.model.Category;
import com.ddicg.erp.modules.merchandise.dto.CategoryDto;
import org.mapstruct.*;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface CategoryMapper extends EntityMapper<CategoryDto, Category> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Category partialUpdate(CategoryDto categoryDto, @MappingTarget Category category);
}