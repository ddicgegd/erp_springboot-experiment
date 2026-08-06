package com.ddicg.erp.repository.specification;

import com.ddicg.erp.model.entity.Attributes;
import jakarta.persistence.criteria.Predicate;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class AttributesSpecification {

    private final List<Specification<Attributes>> specifications = new ArrayList<>();

    public static AttributesSpecification builder() {
        return new AttributesSpecification();
    }



    private static String like(final String value) {
        return "%" + value.toUpperCase() + "%";
    }

    public Specification<Attributes> build() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(specifications.stream()
                .filter(Objects::nonNull)
                .map(s -> s.toPredicate(root, query, criteriaBuilder)).toArray(Predicate[]::new));
    }
}
