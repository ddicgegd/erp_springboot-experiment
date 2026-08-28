package com.ddicg.erp.core.common.repository.specification;

import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.dto.request.OrderSearchRequest;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class OrderSpecification {

    public static Specification<Order> build(OrderSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(request.getOrderNumber())) {
                predicates.add(cb.like(cb.lower(root.get("orderNumber")), "%" + request.getOrderNumber().toLowerCase() + "%"));
            }

            if (StringUtils.hasText(request.getCustomerId())) {
                try {
                    Long custId = Long.valueOf(request.getCustomerId().trim());
                    predicates.add(cb.equal(root.get("customerInfo").get("customerId"), custId));
                } catch (NumberFormatException ignored) {
                }
            }

            if (StringUtils.hasText(request.getCustomerName())) {
                predicates.add(cb.like(cb.lower(root.get("customerInfo").get("customerName")), "%" + request.getCustomerName().toLowerCase() + "%"));
            }

            if (StringUtils.hasText(request.getCustomerEmail())) {
                predicates.add(cb.like(cb.lower(root.get("customerInfo").get("customerEmail")), "%" + request.getCustomerEmail().toLowerCase() + "%"));
            }

            if (StringUtils.hasText(request.getCustomerPhone())) {
                predicates.add(cb.equal(root.get("customerInfo").get("customerPhone"), request.getCustomerPhone()));
            }

            if (request.getOrderStatus() != null) {
                predicates.add(cb.equal(root.get("currentStatus"), request.getOrderStatus()));
            }

            if (request.getStartDate() != null && request.getEndDate() != null) {
                predicates.add(cb.between(root.get("auditInfo").get("createdAt"), request.getStartDate(), request.getEndDate()));
            } else if (request.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("auditInfo").get("createdAt"), request.getStartDate()));
            } else if (request.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("auditInfo").get("createdAt"), request.getEndDate()));
            }

            if (request.getMinAmount() != null && request.getMaxAmount() != null) {
                predicates.add(cb.between(root.get("totalAmount"), request.getMinAmount(), request.getMaxAmount()));
            } else if (request.getMinAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalAmount"), request.getMinAmount()));
            } else if (request.getMaxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("totalAmount"), request.getMaxAmount()));
            }

            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("orderItems", JoinType.LEFT);
            }

            query.distinct(true);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
