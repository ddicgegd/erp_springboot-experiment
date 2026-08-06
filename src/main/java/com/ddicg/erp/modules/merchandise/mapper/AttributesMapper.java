package com.ddicg.erp.modules.merchandise.mapper;

import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.merchandise.dto.AttributesDto;
import org.mapstruct.*;

@Mapper(
        builder = @org.mapstruct.Builder(disableBuilder = true),
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING)
public interface AttributesMapper extends EntityMapper<AttributesDto, Attributes> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "specifications", ignore = true)
    Attributes partialUpdate(AttributesDto xAttributesDto, @MappingTarget Attributes attributes);
    
    @Mapping(target = "specifications", ignore = true)
    AttributesDto toDto(Attributes entity);
    
    @Mapping(target = "specifications", ignore = true)
    Attributes toEntity(AttributesDto dto);
}
