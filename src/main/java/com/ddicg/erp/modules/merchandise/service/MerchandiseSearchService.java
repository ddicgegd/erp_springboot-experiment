package com.ddicg.erp.modules.merchandise.service;

import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.merchandise.model.Category;
import com.ddicg.erp.modules.merchandise.model.Product;
import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.ddicg.erp.core.common.repository.specification.SearchCriteria;
import com.ddicg.erp.core.common.repository.specification.SpecificationBuilder;
import com.ddicg.erp.modules.merchandise.dto.request.AttributesSearchRequest;
import com.ddicg.erp.modules.merchandise.dto.request.CategorySearchRequest;
import com.ddicg.erp.modules.merchandise.dto.request.GetProductRequest;
import com.ddicg.erp.core.common.dto.request.PagingRequest;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MerchandiseSearchService {

    private final EntityManager entityManager;

    public Pageable pageable(PagingRequest paging) {
        if (paging == null) {
            return PageRequest.of(0, 10);
        }
        if (paging.getPage() < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Số trang phải lớn hơn hoặc bằng 1.");
        }
        if (paging.getSize() < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Kích thước trang phải lớn hơn 0.");
        }
        return paging.pageable();
    }

    public Specification<Category> categorySpecification(CategorySearchRequest request) {
        List<SearchCriteria> criteria = new ArrayList<>();

        addIfNotEmpty(criteria, "name", filterBlank(request.getNames()));
        addIfNotEmpty(criteria, "skuInfo.sku", filterBlank(request.getSkus()));

        List<Long> ids = parseLongList(filterBlank(request.getIds()), "ids");
        if (!ids.isEmpty()) {
            criteria.add(new SearchCriteria("id", "~", ids));
        }

        if (StringUtils.hasText(request.getKeyword())) {
            criteria.add(new SearchCriteria("name", "~", request.getKeyword().trim()));
        }
        if (StringUtils.hasText(request.getCreatedBy())) {
            criteria.add(new SearchCriteria("createdBy", "~", request.getCreatedBy().trim()));
        }
        addComparableRange(criteria, "createdAt", request.getCreatedFrom(), request.getCreatedTo());
        addComparableRange(criteria, "updatedAt", request.getUpdatedFrom(), request.getUpdatedTo());

        return this.<Category>activeOnly().and(new SpecificationBuilder<Category>(criteria).build());
    }

    public Specification<Product> productSpecification(GetProductRequest request) {
        Specification<Product> specification = activeOnly();

        specification = specification.and(productKeywordSpecification(request.getKeyword()));
        specification = specification.and(equalsString("createdBy", request.getCreatedBy()));
        specification = specification.and(equalsLong("category.id", request.getCategoryId(), "categoryId"));
        specification = specification.and(inLongList("id", request.getProductIds(), "productIds"));
        specification = specification.and(inStringList("skuInfo.sku", request.getSkus()));
        specification = specification.and(inEnumList("status", request.getStatuses(), ActiveStatus.class, "statuses"));
        specification = specification.and(inLongList("category.id", request.getCategoryIds(), "categoryIds"));
        specification = specification.and(greaterThanOrEqualTo("totalSoldQuantity", request.getMinSoldQuantity()));
        specification = specification.and(lessThanOrEqualTo("totalSoldQuantity", request.getMaxSoldQuantity()));
        specification = specification.and(greaterThanOrEqualTo("totalRevenue", request.getMinRevenue() == null ? null : BigDecimal.valueOf(request.getMinRevenue())));
        specification = specification.and(lessThanOrEqualTo("totalRevenue", request.getMaxRevenue() == null ? null : BigDecimal.valueOf(request.getMaxRevenue())));
        specification = specification.and(greaterThanOrEqualTo("totalOrders", request.getMinOrders()));
        specification = specification.and(lessThanOrEqualTo("totalOrders", request.getMaxOrders()));
        specification = specification.and(greaterThanOrEqualTo("viewCount", request.getMinView()));
        specification = specification.and(greaterThanOrEqualTo("averageRating", request.getMinRating()));
        specification = specification.and(greaterThanOrEqualTo("reviewCount", request.getMinReviews()));
        specification = specification.and(greaterThanOrEqualTo("createdAt", request.getCreatedFrom()));
        specification = specification.and(lessThanOrEqualTo("createdAt", request.getCreatedTo()));
        specification = specification.and(greaterThanOrEqualTo("updatedAt", request.getUpdatedFrom()));
        specification = specification.and(lessThanOrEqualTo("updatedAt", request.getUpdatedTo()));

        return specification;
    }

    public Specification<Attributes> attributesSpecification(AttributesSearchRequest request) {
        List<SearchCriteria> criteria = buildAttributesCriteria(request);
        Specification<Attributes> activeSpecification = activeOnly();
        if (criteria.isEmpty()) {
            return activeSpecification;
        }
        return activeSpecification.and(new SpecificationBuilder<Attributes>(criteria).build());
    }

    public List<Long> searchAttributeIds(AttributesSearchRequest request) {
        Specification<Attributes> spec = attributesSpecification(request);
        Pageable pageable = pageable(request.getPaging());

        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(Long.class);
        Root<Attributes> root = query.from(Attributes.class);
        query.select(root.get("id"));

        Predicate predicate = spec.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
        if (pageable.getSort().isSorted()) {
            List<Order> orders = new ArrayList<>();
            for (Sort.Order sortOrder : pageable.getSort()) {
                Path<?> path = resolvePath(root, sortOrder.getProperty());
                orders.add(sortOrder.isAscending() ? cb.asc(path) : cb.desc(path));
            }
            query.orderBy(orders);
        }

        TypedQuery<Long> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        return typedQuery.getResultList();
    }

    private List<SearchCriteria> buildAttributesCriteria(AttributesSearchRequest request) {
        List<SearchCriteria> criteria = new ArrayList<>();

        if (StringUtils.hasText(request.getKeyword())) {
            criteria.add(new SearchCriteria("name", "~", request.getKeyword().trim()));
        }

        List<Long> ids = parseLongList(filterBlank(request.getIds()), "ids");
        if (!ids.isEmpty()) {
            criteria.add(new SearchCriteria("id", "~", ids));
        }

        List<Long> productIds = parseLongList(filterBlank(request.getProductIds()), "productIds");
        if (!productIds.isEmpty()) {
            criteria.add(new SearchCriteria("product.id", "~", productIds));
        }

        if (StringUtils.hasText(request.getProductId())) {
            criteria.add(new SearchCriteria("product.id", ":", parseLong(request.getProductId(), "productId")));
        }

        addIfNotEmpty(criteria, "sku.sku", filterBlank(request.getSkus()));

        List<StockStatus> statuses = parseEnumList(filterBlank(request.getStatuses()), StockStatus.class, "statuses");
        if (!statuses.isEmpty()) {
            criteria.add(new SearchCriteria("statusProduct", "~", statuses));
        }

        addComparableRange(criteria, "price", request.getMinPrice(), request.getMaxPrice());
        addComparableRange(criteria, "salePrice", request.getMinSalePrice(), request.getMaxSalePrice());
        addComparableRange(criteria, "soldQuantity", request.getMinSoldQuantity(), request.getMaxSoldQuantity());
        addComparableRange(criteria, "costPrice", request.getMinCostPrice(), request.getMaxCostPrice());

        if (StringUtils.hasText(request.getCreatedBy())) {
            criteria.add(new SearchCriteria("createdBy", "~", request.getCreatedBy().trim()));
        }
        addComparableRange(criteria, "createdAt", request.getCreatedFrom(), request.getCreatedTo());
        addComparableRange(criteria, "updatedAt", request.getUpdatedFrom(), request.getUpdatedTo());

        return criteria;
    }

    private Specification<Product> productKeywordSpecification(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }

        String normalizedKeyword = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            query.distinct(true);

            Join<Product, Attributes> attributesJoin = root.join("attributes", JoinType.LEFT);
            Join<Attributes, String> keywordsJoin = attributesJoin.join("keywords", JoinType.LEFT);

            Predicate productName = cb.like(cb.lower(root.get("name")), normalizedKeyword);
            Predicate productSku = cb.like(cb.lower(root.get("skuInfo").get("sku")), normalizedKeyword);
            Predicate attributeName = cb.like(cb.lower(attributesJoin.get("name")), normalizedKeyword);
            Predicate attributeSku = cb.like(cb.lower(attributesJoin.get("sku").get("sku")), normalizedKeyword);
            Predicate attributeKeyword = cb.like(cb.lower(keywordsJoin), normalizedKeyword);

            return cb.or(productName, productSku, attributeName, attributeSku, attributeKeyword);
        };
    }

    private void addIfNotEmpty(List<SearchCriteria> criteria, String path, List<String> values) {
        if (!values.isEmpty()) {
            criteria.add(new SearchCriteria(path, "~", values));
        }
    }

    private void addComparableRange(List<SearchCriteria> criteria, String path, Comparable<?> from, Comparable<?> to) {
        if (from != null) {
            criteria.add(new SearchCriteria(path, ">", from));
        }
        if (to != null) {
            criteria.add(new SearchCriteria(path, "<", to));
        }
    }

    private <T> Specification<T> activeOnly() {
        return (root, query, cb) -> cb.and(
                cb.or(
                        cb.isNull(root.get("isDeleted")),
                        cb.isFalse(root.get("isDeleted"))),
                cb.or(
                        cb.isNull(root.get("deletedAt")),
                        cb.greaterThan(root.get("deletedAt"), LocalDateTime.now())));
    }

    private Specification<Product> equalsString(String path, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedValue = value.trim().toLowerCase();
        return (root, query, cb) -> cb.equal(cb.lower(resolvePath(root, path).as(String.class)), normalizedValue);
    }

    private Specification<Product> equalsLong(String path, String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        Long parsedValue = parseLong(value, fieldName);
        return (root, query, cb) -> cb.equal(resolvePath(root, path).as(Long.class), parsedValue);
    }

    private Specification<Product> inStringList(String path, List<String> values) {
        List<String> filteredValues = filterBlank(values).stream()
                .map(String::trim)
                .toList();
        if (filteredValues.isEmpty()) {
            return null;
        }

        return (root, query, cb) -> resolvePath(root, path).in(filteredValues);
    }

    private Specification<Product> inLongList(String path, List<String> values, String fieldName) {
        List<Long> parsedValues = parseLongList(filterBlank(values), fieldName);
        if (parsedValues.isEmpty()) {
            return null;
        }

        return (root, query, cb) -> resolvePath(root, path).in(parsedValues);
    }

    private <E extends Enum<E>> Specification<Product> inEnumList(
            String path,
            List<String> values,
            Class<E> enumType,
            String fieldName) {
        List<E> parsedValues = parseEnumList(filterBlank(values), enumType, fieldName);
        if (parsedValues.isEmpty()) {
            return null;
        }

        return (root, query, cb) -> resolvePath(root, path).in(parsedValues);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Specification<Product> greaterThanOrEqualTo(String path, Comparable<?> value) {
        if (value == null) {
            return null;
        }

        return (root, query, cb) -> cb.greaterThanOrEqualTo(
                (Expression) resolvePath(root, path),
                (Comparable) value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Specification<Product> lessThanOrEqualTo(String path, Comparable<?> value) {
        if (value == null) {
            return null;
        }

        return (root, query, cb) -> cb.lessThanOrEqualTo(
                (Expression) resolvePath(root, path),
                (Comparable) value);
    }

    public List<Long> parseLongList(List<String> values, String fieldName) {
        return filterBlank(values).stream()
                .map(value -> parseLong(value, fieldName))
                .toList();
    }

    public Long parseLong(String value, String fieldName) {
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    fieldName + " phải là số nguyên hợp lệ.", e);
        }
    }

    public <E extends Enum<E>> List<E> parseEnumList(List<String> values, Class<E> enumType, String fieldName) {
        return filterBlank(values).stream()
                .map(value -> parseEnum(value, enumType, fieldName))
                .toList();
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumType, String fieldName) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    fieldName + " chứa trạng thái không hợp lệ: " + value, e);
        }
    }

    private List<String> filterBlank(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private <T> Path<T> resolvePath(From<?, ?> from, String path) {
        Path<?> current = from;
        for (String segment : path.split("\\.")) {
            current = current.get(segment);
        }
        return (Path<T>) current;
    }
}
