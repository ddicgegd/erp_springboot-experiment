package com.ddicg.erp.service.dto;

import lombok.Data;
import java.util.List;

@Data
public class VariantOptionDto {
    private String name;
    private List<String> values;
}
