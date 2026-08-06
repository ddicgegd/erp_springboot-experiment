package com.ddicg.erp.modules.merchandise.mapper;

import com.ddicg.erp.core.common.model.embedded.Promotion;
import com.ddicg.erp.modules.merchandise.dto.PromotionDto;
import org.mapstruct.Mapper;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), componentModel = "spring")
public interface PromotionMapper extends EntityMapper<PromotionDto, Promotion> {
}
