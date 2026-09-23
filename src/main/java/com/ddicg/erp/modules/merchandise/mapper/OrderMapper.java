package com.ddicg.erp.modules.merchandise.mapper;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.modules.order.dto.OrderDto;
import com.ddicg.erp.modules.order.dto.response.MyOrderDetailResponse;
import com.ddicg.erp.modules.order.dto.response.MyOrderListResponse;
import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.model.OrderItem;
import org.mapstruct.*;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), 
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {OrderItemMapper.class}
)
public interface OrderMapper extends EntityMapper<OrderDto, Order> {

    @Named("toDto")
    @Mapping(target = "currentStatus", expression = "java(order.getStatus() != null && !order.getStatus().isEmpty() ? order.getStatus().get(order.getStatus().size() - 1) : null)")
    @Mapping(target = "currentStatusDescription", expression = "java(order.getStatus() != null && !order.getStatus().isEmpty() ? order.getStatus().get(order.getStatus().size() - 1).getDisplayName() : null)")
    @Mapping(target = "customerId", expression = "java(order.getCustomerInfo() != null && order.getCustomerInfo().getCustomerId() != null ? String.valueOf(order.getCustomerInfo().getCustomerId()) : null)")
    @Mapping(target = "customerName", source = "customerInfo.customerName")
    @Mapping(target = "customerEmail", source = "customerInfo.customerEmail")
    @Mapping(target = "customerPhone", source = "customerInfo.customerPhone")
    @Mapping(target = "shippingAddress", source = "customerInfo.shippingAddress")
    OrderDto toDto(Order order);

    @IterableMapping(qualifiedByName = "toDto")
    List<OrderDto> toDto(List<Order> entityList);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Order partialUpdate(OrderDto orderDto, @MappingTarget Order order);

    default MyOrderListResponse toMyOrderListResponse(Order order) {
        if (order == null) return null;

        List<String> productNames = order.getOrderItems() != null
                ? order.getOrderItems().stream()
                    .map(it -> {
                        if (it.getVariantOptions() != null && !it.getVariantOptions().isEmpty()) {
                            String options = it.getVariantOptions().stream()
                                    .flatMap(vo -> vo.getValues() != null ? vo.getValues().stream() : java.util.stream.Stream.empty())
                                    .filter(Objects::nonNull)
                                    .collect(java.util.stream.Collectors.joining(" • "));
                            if (!options.isBlank()) {
                                String skuPrefix = it.getAttributesSku() != null ? it.getAttributesSku() : "";
                                return skuPrefix + (!skuPrefix.isBlank() ? " • " : "") + options;
                            }
                        }
                        return it.getAttributesSku();
                    })
                    .filter(Objects::nonNull)
                    .toList()
                : Collections.emptyList();

        OrderStatus currentStatus = order.getCurrentStatus() != null
                ? order.getCurrentStatus()
                : (order.getStatus() != null && !order.getStatus().isEmpty()
                    ? order.getStatus().get(order.getStatus().size() - 1)
                    : null);

        String currentStatusDescription = currentStatus != null ? currentStatus.getDisplayName() : null;

        return MyOrderListResponse.builder()
                .orderNumber(order.getOrderNumber())
                .productNames(productNames)
                .orderDate(order.getAuditInfo() != null ? order.getAuditInfo().getCreatedAt() : null)
                .createdAt(order.getAuditInfo() != null ? order.getAuditInfo().getCreatedAt() : null)
                .totalAmount(order.getTotalAmount())
                .currentStatus(currentStatus)
                .currentStatusDescription(currentStatusDescription)
                .build();
    }

    @Mapping(target = "currentStatus", expression = "java(order.getStatus() != null && !order.getStatus().isEmpty() ? order.getStatus().get(order.getStatus().size() - 1) : null)")
    @Mapping(target = "currentStatusDescription", expression = "java(order.getStatus() != null && !order.getStatus().isEmpty() ? order.getStatus().get(order.getStatus().size() - 1).getDisplayName() : null)")
    @Mapping(target = "customerId", source = "customerInfo.customerId")
    @Mapping(target = "customerName", source = "customerInfo.customerName")
    @Mapping(target = "customerEmail", source = "customerInfo.customerEmail")
    @Mapping(target = "customerPhone", source = "customerInfo.customerPhone")
    @Mapping(target = "shippingAddress", source = "customerInfo.shippingAddress")
    @Mapping(target = "shippingMethod", expression = "java(order.getShippingMethod() != null ? (com.ddicg.erp.core.common.model.enums.ShippingMethod.fromString(order.getShippingMethod()) != null ? com.ddicg.erp.core.common.model.enums.ShippingMethod.fromString(order.getShippingMethod()).getDescription() : order.getShippingMethod()) : null)")
    @Mapping(target = "createdAt", source = "auditInfo.createdAt")
    MyOrderDetailResponse toMyOrderDetailResponse(Order order);
}
