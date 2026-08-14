package com.ddicg.erp.modules.merchandise.mapper;

import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.dto.OrderDto;
import org.mapstruct.*;
import java.util.List;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), 
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {OrderItemMapper.class}
)
public interface OrderMapper extends EntityMapper<OrderDto, Order> {

    @Named("toDto")
    @Mapping(target = "currentStatus", expression = "java(order.getStatus() != null && !order.getStatus().isEmpty() ? order.getStatus().get(order.getStatus().size() - 1) : null)")
    @Mapping(target = "currentStatusDescription", expression = "java(order.getStatus() != null && !order.getStatus().isEmpty() ? order.getStatus().get(order.getStatus().size() - 1).getDescription() : null)")
    @Mapping(target = "customerId", source = "customerInfo.customerId")
    @Mapping(target = "customerName", source = "customerInfo.customerName")
    @Mapping(target = "customerEmail", source = "customerInfo.customerEmail")
    @Mapping(target = "customerPhone", source = "customerInfo.customerPhone")
    @Mapping(target = "shippingAddress", source = "customerInfo.shippingAddress")
    OrderDto toDto(Order order);

    @IterableMapping(qualifiedByName = "toDto")
    List<OrderDto> toDto(List<Order> entityList);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Order partialUpdate(OrderDto orderDto, @MappingTarget Order order);
}
