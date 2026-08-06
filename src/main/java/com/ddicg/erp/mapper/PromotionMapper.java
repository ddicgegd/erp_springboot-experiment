package com.ddicg.erp.mapper;

import com.ddicg.erp.model.embedded.Promotion;
import com.ddicg.erp.service.dto.PromotionDto;
import org.mapstruct.Mapper;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), componentModel = "spring")
public interface PromotionMapper extends EntityMapper<PromotionDto, Promotion> {
}
