package com.ddicg.erp.repository.specification;

import com.ddicg.erp.model.enums.SearchOperation;
import jakarta.persistence.criteria.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SpecificationBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(SpecificationBuilder.class);

    private final List<SearchCriteria> params;

    public SpecificationBuilder() {
        this.params = new ArrayList<>();
    }

    public SpecificationBuilder(List<SearchCriteria> params) {
        this.params = params;
    }

    public SpecificationBuilder<T> with(String key, String operation, Object value) {
        params.add(new SearchCriteria(key, operation, value));
        return this;
    }

    public SpecificationBuilder<T> with(String key, SearchOperation operation, Object value) {
        params.add(new SearchCriteria(key, operation, value));
        return this;
    }

    public Specification<T> build() {
        if (params.isEmpty()) {
            return Specification.where(null);
        }

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            for (SearchCriteria criteria : params) {
                String key = criteria.getKey();
                SearchOperation op = criteria.getOperation();
                Object value = criteria.getValue();

                if (value == null) continue;

                Path<?> path = resolvePath(root, key);

                switch (op) {
                    case EQUALITY -> predicates.add(buildEquality(criteriaBuilder, path, value));
                    case NEGATION -> predicates.add(buildNegation(criteriaBuilder, path, value));
                    case GREATER_THAN -> predicates.add(buildGreaterThan(criteriaBuilder, path, value));
                    case LESS_THAN -> predicates.add(buildLessThan(criteriaBuilder, path, value));
                    case LIKE -> predicates.add(buildLike(criteriaBuilder, path, value));
                    case STARTS_WITH -> predicates.add(buildStartsWith(criteriaBuilder, path, value));
                    case ENDS_WITH -> predicates.add(buildEndsWith(criteriaBuilder, path, value));
                    case CONTAINS -> predicates.add(buildContains(criteriaBuilder, path, value));
                    case IN -> predicates.add(buildIn(path, value));
                }
            }

            log.debug("SpecificationBuilder generated {} predicates for params: {}", predicates.size(), params);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate buildEquality(CriteriaBuilder cb, Path<?> path, Object value) {
        Path<String> strPath = (Path<String>) path;
        if (value instanceof String s) {
            String val = s.toLowerCase();
            return cb.equal(cb.lower(strPath), val);
        }
        return cb.equal(path, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate buildNegation(CriteriaBuilder cb, Path<?> path, Object value) {
        Path<String> strPath = (Path<String>) path;
        if (value instanceof String s) {
            return cb.notEqual(cb.lower(strPath), s.toLowerCase());
        }
        return cb.notEqual(path, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate buildGreaterThan(CriteriaBuilder cb, Path<?> path, Object value) {
        if (value instanceof Comparable comparable) {
            Path<Comparable> compPath = (Path<Comparable>) path;
            return cb.greaterThan(compPath, comparable);
        }
        return cb.equal(path, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate buildLessThan(CriteriaBuilder cb, Path<?> path, Object value) {
        if (value instanceof Comparable comparable) {
            Path<Comparable> compPath = (Path<Comparable>) path;
            return cb.lessThan(compPath, comparable);
        }
        return cb.equal(path, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate buildLike(CriteriaBuilder cb, Path<?> path, Object value) {
        if (value instanceof String strValue) {
            Path<String> strPath = (Path<String>) path;
            return cb.like(cb.lower(strPath), "%" + strValue.toLowerCase() + "%", '\\');
        }
        if (value instanceof List<?> listValue && !listValue.isEmpty()) {
            return path.in(listValue);
        }
        if (value instanceof Enum) {
            return cb.equal(path, value);
        }
        return cb.equal(path, value);
    }

    @SuppressWarnings("unchecked")
    private Predicate buildStartsWith(CriteriaBuilder cb, Path<?> path, Object value) {
        if (value instanceof String strValue) {
            Path<String> strPath = (Path<String>) path;
            return cb.like(cb.lower(strPath), strValue.toLowerCase() + "%", '\\');
        }
        return cb.equal(path, value);
    }

    @SuppressWarnings("unchecked")
    private Predicate buildEndsWith(CriteriaBuilder cb, Path<?> path, Object value) {
        if (value instanceof String strValue) {
            Path<String> strPath = (Path<String>) path;
            return cb.like(cb.lower(strPath), "%" + strValue.toLowerCase(), '\\');
        }
        return cb.equal(path, value);
    }

    @SuppressWarnings("unchecked")
    private Predicate buildContains(CriteriaBuilder cb, Path<?> path, Object value) {
        if (value instanceof String strValue) {
            Path<String> strPath = (Path<String>) path;
            return cb.like(cb.lower(strPath), "%" + strValue.toLowerCase() + "%", '\\');
        }
        return cb.equal(path, value);
    }

    private Predicate buildIn(Path<?> path, Object value) {
        if (value instanceof List<?> listValue && !listValue.isEmpty()) {
            return path.in(listValue);
        }
        return null;
    }

    private Path<?> resolvePath(Root<T> root, String key) {
        String[] parts = key.split("\\.");
        Path<?> path = root;
        for (String part : parts) {
            path = path.get(part);
        }
        return path;
    }
}
