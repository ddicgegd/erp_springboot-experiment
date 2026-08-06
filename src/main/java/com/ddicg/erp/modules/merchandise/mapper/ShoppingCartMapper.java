package com.ddicg.erp.modules.merchandise.mapper;

import com.ddicg.erp.modules.cart.model.ShoppingCart;
import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
import org.mapstruct.*;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ShoppingCartMapper extends EntityMapper<ShoppingCartDto, ShoppingCart> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ShoppingCart partialUpdate(ShoppingCartDto shoppingCartDto, @MappingTarget ShoppingCart shoppingCart);
}
