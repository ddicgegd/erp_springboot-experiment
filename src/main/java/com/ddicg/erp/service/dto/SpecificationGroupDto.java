package com.ddicg.erp.service.dto;

import lombok.Data;
import java.util.List;

@Data
public class SpecificationGroupDto {
    private String groupName;
    private List<SpecificationDto> specifications;
}
