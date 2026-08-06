package com.ddicg.erp.mapper;

import com.ddicg.erp.model.entity.ShoppingCart;
import com.ddicg.erp.service.dto.ShoppingCartDto;
import org.mapstruct.*;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ShoppingCartMapper extends EntityMapper<ShoppingCartDto, ShoppingCart>{
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ShoppingCart partialUpdate(ShoppingCartDto shoppingCartDto, @MappingTarget ShoppingCart shoppingCart);
}
