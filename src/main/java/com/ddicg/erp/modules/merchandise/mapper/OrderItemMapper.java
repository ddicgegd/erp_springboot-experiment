package com.ddicg.erp.modules.merchandise.mapper;

import com.ddicg.erp.modules.order.model.OrderItem;
import com.ddicg.erp.modules.order.dto.OrderItemDto;
import org.mapstruct.*;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), 
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING
)
public interface OrderItemMapper extends EntityMapper<OrderItemDto, OrderItem> {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    OrderItem partialUpdate(OrderItemDto orderItemDto, @MappingTarget OrderItem orderItem);
}
