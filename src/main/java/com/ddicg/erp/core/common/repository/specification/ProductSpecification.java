package com.ddicg.erp.core.common.repository.specification;

import com.ddicg.erp.modules.merchandise.model.Product;
import jakarta.persistence.criteria.Predicate;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class ProductSpecification {
    private final String FIELD_NAME = "name";
    private final String FIELD_SKU = "sku";
    private final String FIELD_STATUS = "status";

    private final List<Specification<Product>> specifications = new ArrayList<>();

    public static ProductSpecification builder() {
        return new ProductSpecification();
    }

    public ProductSpecification withName(final String name) {
        if (!ObjectUtils.isEmpty(name)) {
            specifications.add(
                    (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.upper(root.get(FIELD_NAME)),
                            like(name)));
        }
        return this;
    }

    public ProductSpecification withSku(final String sku) {
        if (!ObjectUtils.isEmpty(sku)) {
            specifications.add(
                    (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.upper(root.get(FIELD_SKU)),
                            like(sku)));
        }
        return this;
    }

    public ProductSpecification withStatus(final String status) {
        if (!ObjectUtils.isEmpty(status)) {
            specifications.add(
                    (root, query, criteriaBuilder) -> criteriaBuilder
                            .like(criteriaBuilder.upper(root.get(FIELD_STATUS)), like(status)));
        }
        return this;
    }

    private static String like(final String value) {
        return "%" + value.toUpperCase() + "%";
    }

    public Specification<Product> build() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(specifications.stream()
                .filter(Objects::nonNull)
                .map(s -> s.toPredicate(root, query, criteriaBuilder)).toArray(Predicate[]::new));
    }
}
