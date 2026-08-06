package com.ddicg.erp.core.common.repository.specification;

import com.ddicg.erp.core.common.model.enums.SearchOperation;

public class SearchCriteria {
    private String key;
    private SearchOperation operation;
    private Object value;

    public SearchCriteria() {}

    public SearchCriteria(String key, SearchOperation operation, Object value) {
        this.key = key;
        this.operation = operation;
        this.value = value;
    }

    public SearchCriteria(String key, String operationStr, Object value) {
        this.key = key;
        this.operation = parseOperation(operationStr);
        this.value = value;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public SearchOperation getOperation() { return operation; }
    public void setOperation(SearchOperation operation) { this.operation = operation; }
    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }

    private SearchOperation parseOperation(String op) {
        if (op == null) return SearchOperation.EQUALITY;
        switch (op) {
            case ":": return SearchOperation.EQUALITY;
            case "!": return SearchOperation.NEGATION;
            case ">": return SearchOperation.GREATER_THAN;
            case "<": return SearchOperation.LESS_THAN;
            case "~": return SearchOperation.LIKE;
            default: return SearchOperation.EQUALITY;
        }
    }
}
