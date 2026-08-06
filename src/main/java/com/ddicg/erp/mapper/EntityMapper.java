package com.ddicg.erp.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

public interface EntityMapper<D, E> {
    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    E toEntity(D dto);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    D toDto(E entity);

    List<D> toDto(List<E> entityList);

    List<E> toEntity(List<D> dtoList);

}
