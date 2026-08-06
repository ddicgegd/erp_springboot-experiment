package com.ddicg.erp.repository.specification;

import com.ddicg.erp.model.enums.SearchOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchCriteria {
    private String key;
    private SearchOperation operation;
    private Object value;
    public SearchCriteria(String key, String operationStr, Object value) {
        this.key = key;
        this.operation = parseOperation(operationStr);
        this.value = value;
    }

    private SearchOperation parseOperation(String op) {
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
