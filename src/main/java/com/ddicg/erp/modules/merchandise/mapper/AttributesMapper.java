package com.ddicg.erp.modules.merchandise.mapper;

import com.ddicg.erp.core.common.model.embedded.Specification;
import com.ddicg.erp.core.common.model.embedded.SpecificationGroup;
import com.ddicg.erp.core.common.model.embedded.Specificationa;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.merchandise.dto.AttributesDto;
import com.ddicg.erp.modules.merchandise.dto.SpecificationDto;
import com.ddicg.erp.modules.merchandise.dto.SpecificationGroupDto;
import org.mapstruct.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mapper(
        builder = @org.mapstruct.Builder(disableBuilder = true),
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING)
public interface AttributesMapper extends EntityMapper<AttributesDto, Attributes> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "specifications", ignore = true)
    Attributes partialUpdate(AttributesDto xAttributesDto, @MappingTarget Attributes attributes);
    
    AttributesDto toDto(Attributes entity);
    
    @Mapping(target = "specifications", ignore = true)
    Attributes toEntity(AttributesDto dto);

    default SpecificationGroupDto toDto(SpecificationGroup group) {
        if (group == null) {
            return null;
        }

        SpecificationGroupDto dto = new SpecificationGroupDto();
        dto.setGroupName(group.getGroupName());
        dto.setSpecifications(toSpecificationDtos(group.getSpecifications()));
        return dto;
    }

    default List<SpecificationDto> toSpecificationDtos(List<Object> specifications) {
        if (specifications == null) {
            return null;
        }

        List<SpecificationDto> dtos = new ArrayList<>(specifications.size());
        for (Object specification : specifications) {
            dtos.add(toSpecificationDto(specification));
        }
        return dtos;
    }

    default SpecificationDto toSpecificationDto(Object specification) {
        if (specification == null) {
            return null;
        }
        if (specification instanceof SpecificationDto dto) {
            return dto;
        }
        if (specification instanceof Specification spec) {
            return new SpecificationDto(spec.getKey(), spec.getData());
        }
        if (specification instanceof Specificationa spec) {
            return new SpecificationDto(spec.getName(), spec.getValue());
        }
        if (specification instanceof Map<?, ?> spec) {
            return new SpecificationDto(asString(spec.get("key")), firstString(spec, "data", "value"));
        }

        return null;
    }

    default String firstString(Map<?, ?> source, String firstKey, String secondKey) {
        String value = asString(source.get(firstKey));
        return value != null ? value : asString(source.get(secondKey));
    }

    default String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
