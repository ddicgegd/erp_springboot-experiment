package com.ddicg.erp.modules.merchandise.mapper;

import com.ddicg.erp.core.common.model.embedded.Specificationa;
import com.ddicg.erp.modules.merchandise.dto.SpecificationDto;
import org.mapstruct.*;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface SpecificationMapper extends EntityMapper<SpecificationDto, Specificationa> {
    @Override
    @Mapping(target = "key", source = "name")
    @Mapping(target = "data", source = "value")
    SpecificationDto toDto(Specificationa entity);

    @Override
    @Mapping(target = "name", source = "key")
    @Mapping(target = "value", source = "data")
    Specificationa toEntity(SpecificationDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "name", source = "key")
    @Mapping(target = "value", source = "data")
    Specificationa partialUpdate(SpecificationDto specificationDto, @MappingTarget Specificationa specificationa);
}
