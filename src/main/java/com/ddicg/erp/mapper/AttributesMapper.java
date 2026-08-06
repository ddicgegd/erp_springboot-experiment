package com.ddicg.erp.mapper;

import com.ddicg.erp.model.entity.Attributes;
import com.ddicg.erp.service.dto.AttributesDto;
import org.mapstruct.*;

@Mapper(
        builder = @org.mapstruct.Builder(disableBuilder = true),
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = SpecificationMapper.class)
public interface AttributesMapper extends EntityMapper<AttributesDto, Attributes> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Attributes partialUpdate(AttributesDto xAttributesDto, @MappingTarget Attributes attributes);
}
