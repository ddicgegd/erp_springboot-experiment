package com.ddicg.erp.mapper;

import com.ddicg.erp.model.entity.OrderItem;
import com.ddicg.erp.service.dto.OrderItemDto;
import org.mapstruct.*;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), 
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING
)
public interface OrderItemMapper extends EntityMapper<OrderItemDto, OrderItem> {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    OrderItem partialUpdate(OrderItemDto orderItemDto, @MappingTarget OrderItem orderItem);
}
