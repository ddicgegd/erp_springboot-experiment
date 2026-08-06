package com.ddicg.erp.modules.merchandise.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetProductRequest {
    String name;
    String description;
}
