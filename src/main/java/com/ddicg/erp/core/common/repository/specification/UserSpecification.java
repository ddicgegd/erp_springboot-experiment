package com.ddicg.erp.core.common.repository.specification;

import com.ddicg.erp.modules.iam.model.User;
import jakarta.persistence.criteria.Predicate;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class UserSpecification {
    private static final String FIELD_FULL_NAME = "fullName";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_PHONE_NUMBER = "phoneNumber";

    private final List<Specification<User>> specifications = new ArrayList<>();

    public static UserSpecification builder() {
        return new UserSpecification();
    }

    public UserSpecification withFullName(final String fullName) {
        if (!ObjectUtils.isEmpty(fullName)) {
            specifications.add(
                (root, query, criteriaBuilder) ->
                        criteriaBuilder.like(criteriaBuilder.upper(root.get(FIELD_FULL_NAME)), like(fullName)));
        }
        return this;
    }

    public UserSpecification withEmail(final String email) {
        if (!ObjectUtils.isEmpty(email)) {
            specifications.add(
                    (root, query, criteriaBuilder) ->
                            criteriaBuilder.like(criteriaBuilder.upper(root.get(FIELD_EMAIL)), like(email)));
        }
        return this;
    }

    public UserSpecification withPhoneNumber(final String phoneNumber) {
        if (!ObjectUtils.isEmpty(phoneNumber)) {
            specifications.add(
                    (root, query, criteriaBuilder) ->
                            criteriaBuilder.like(criteriaBuilder.upper(root.get(FIELD_PHONE_NUMBER)), like(phoneNumber)));
        }
        return this;
    }

    private static String like(final String value) {
        return "%" + value.toUpperCase() + "%";
    }

    public Specification<User> build() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(specifications.stream()
                .filter(Objects::nonNull)
                .map(s -> s.toPredicate(root, query, criteriaBuilder)).toArray(Predicate[]::new));
    }
}
