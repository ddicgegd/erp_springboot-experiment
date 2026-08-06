package com.ddicg.erp.modules.merchandise.mapper;

import com.ddicg.erp.modules.order.model.Payment;
import com.ddicg.erp.modules.merchandise.dto.PaymentDto;
import org.mapstruct.*;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface PaymentMapper extends EntityMapper<PaymentDto, Payment> {
    @Mapping(target = "order", ignore = true)
    Payment toEntity(PaymentDto dto);
}
