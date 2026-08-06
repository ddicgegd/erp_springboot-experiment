package com.ddicg.erp.model.enums;

import lombok.Getter;

@Getter
public enum SearchOperation {
    EQUALITY(":"), NEGATION("!"), GREATER_THAN(">"), LESS_THAN("<"), LIKE("~"), STARTS_WITH(""), ENDS_WITH(""), CONTAINS(""), IN("IN");

    private final String symbol;

    SearchOperation(String symbol) {
        this.symbol = symbol;
    }

}
