package com.ddicg.erp.repository.specification;

import com.ddicg.erp.model.entity.Category;
import jakarta.persistence.criteria.Predicate;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class CategorySpecification {

    private static final String FIELD_NAME = "name";
    private final List<Specification<Category>> specifications = new ArrayList<>();

    public static CategorySpecification builder() {
        return new CategorySpecification();
    }

    public CategorySpecification withName(final String name) {
        if (!ObjectUtils.isEmpty(name)) {
            specifications.add(
                    (root, query, criteriaBuilder) ->
                            criteriaBuilder.like(criteriaBuilder.upper(root.get(FIELD_NAME)), like(name)));
        }
        return this;
    }

    private static String like(final String value) {
        return "%" + value.toUpperCase() + "%";
    }

    public Specification<Category> build() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(specifications.stream()
                .filter(Objects::nonNull)
                .map(s -> s.toPredicate(root, query, criteriaBuilder)).toArray(Predicate[]::new));
    }

}
