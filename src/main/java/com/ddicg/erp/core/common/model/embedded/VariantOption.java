package com.ddicg.erp.core.common.model.embedded;

import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;

@Embeddable
public class VariantOption {
    private String name;
    private List<String> values;

    public VariantOption() {}
    public VariantOption(String name, List<String> values) {
        this.name = name;
        this.values = values;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values != null ? values : new ArrayList<>(); }
}
