package com.ddicg.erp.mapper;

import com.ddicg.erp.model.entity.Payment;
import com.ddicg.erp.service.dto.PaymentDto;
import org.mapstruct.*;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING, uses = {OrderMapper.class})
public interface PaymentMapper extends EntityMapper<PaymentDto, Payment> {
}
