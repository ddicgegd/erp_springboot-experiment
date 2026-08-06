package com.ddicg.erp.core.common.model.enums;

public enum ShoppingCartType {
    ACTIVE("Active"),
    ABANDONED("Abandoned");

    private final String description;

    ShoppingCartType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
