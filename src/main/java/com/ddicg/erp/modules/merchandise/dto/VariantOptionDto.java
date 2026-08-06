package com.ddicg.erp.modules.merchandise.dto;

import java.io.Serializable;
import java.util.List;

public class VariantOptionDto implements Serializable {
    private String name;
    private List<String> values;

    public VariantOptionDto() {}
    public VariantOptionDto(String name, List<String> values) {
        this.name = name;
        this.values = values;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }
}
