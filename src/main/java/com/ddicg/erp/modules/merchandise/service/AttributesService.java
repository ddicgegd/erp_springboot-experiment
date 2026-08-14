package com.ddicg.erp.modules.merchandise.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.modules.merchandise.mapper.AttributesMapper;
import com.ddicg.erp.modules.merchandise.mapper.PromotionMapper;
import com.ddicg.erp.modules.merchandise.mapper.SpecificationMapper;
import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.core.common.model.embedded.SpecificationGroup;
import com.ddicg.erp.core.common.model.embedded.VariantOption;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.merchandise.model.Product;
import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import com.ddicg.erp.modules.merchandise.repository.ProductRepository;
import com.ddicg.erp.modules.merchandise.dto.AttributesDto;
import com.ddicg.erp.core.common.dto.request.PagingRequest;
import com.ddicg.erp.modules.merchandise.dto.request.AttributesSearchRequest;
import com.ddicg.erp.modules.merchandise.dto.request.CreateAttributesRequest;
import com.ddicg.erp.modules.merchandise.dto.request.UpdateAttributesRequest;
import com.ddicg.erp.core.common.service.RedisProducerService;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.merchandise.service.iAttributes;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.core.config.cache.CacheConfig;
import com.ddicg.erp.core.config.cache.CacheUtils;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import org.springframework.transaction.annotation.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttributesService implements iAttributes {

  private static final String SMART_SEARCH_DISTINGUISH_PREFIX = "attributes:smart-search:";
  private static final com.github.benmanes.caffeine.cache.Cache<String, SmartSearchState> SMART_SEARCH_CACHE =
      com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
          .expireAfterAccess(Duration.ofSeconds(30))
          .maximumSize(512)
          .recordStats()
          .build();

  private final AttributesRepository attributesRepository;
  private final ProductRepository productRepository;
  private final SpecificationMapper specificationMapper;
  private final PromotionMapper promotionMapper;
  private final AttributesMapper attributesMapper;
  private final SecurityUtil securityUtil;
  private final org.springframework.cache.CacheManager cacheManager;
  private final RedisProducerService redisProducerService;
  private final MerchandiseSearchService merchandiseSearchService;

  @Override
  @Transactional
  @CacheEvict(value = "attributes", allEntries = true)
  public Response<List<AttributesDto>> create(@NonNull CreateAttributesRequest request) {
    Product product = productRepository
        .findProductBySkuInfo_Sku(request.getProductSku())
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại."));

    List<Attributes> attributesList = new ArrayList<>();

    for (com.ddicg.erp.modules.merchandise.dto.request.AttributeInput item : request.getAttributes()) {
      Attributes attr = Attributes.builder()
          .product(product)
          .sku(SkuInfo.builder()
              .sku(new SkuInfo().createSku("attr-").getSku()
                  .replaceFirst("-", "-" + request.getProductSku().substring(request.getProductSku().length() - 3)))
              .build())
          .name(request.getName())
          .price(item.getPrice() != null ? item.getPrice().doubleValue() : 0.0)
          .salePrice(item.getSalePrice() != null ? item.getSalePrice().doubleValue() : 0.0)
          .variantOptions((item.getVariantOptions() != null && !item.getVariantOptions().isEmpty()) ? item.getVariantOptions() : new ArrayList<>())
          .keywords(request.getKeywords())
          .promotions((item.getPromotions() != null && !item.getPromotions().isEmpty())
              ? item.getPromotions()
              : new ArrayList<>())
          .specifications((item.getSpecifications() != null && !item.getSpecifications().isEmpty())
              ? item.getSpecifications()
              : new ArrayList<>())
          .statusProduct(item.getStatusProduct() != null ? item.getStatusProduct() : StockStatus.NOT_ACTIVE)
          .build();

      attributesList.add(attr);
    }

    List<Attributes> savedList = attributesRepository.saveAll(attributesList);

    log.info("Đã tạo {} attributes cho sản phẩm {}",
        savedList.size(),
        product.getName());

    String message = savedList.size() == 1
        ? "Tạo attributes '" + savedList.getFirst().getName() + "' thành công."
        : String.format("Đã tạo thành công %d variants.", savedList.size());

    attributesMapper.toDto(savedList);

    // Hook: Gửi yêu cầu xóa cache bất đồng bộ qua Redis Stream
    redisProducerService.sendEvictMessage(product.getId().toString());
    SMART_SEARCH_CACHE.invalidateAll();

    return Response.ok(message);
  }

  @Override
  @Transactional
  @CacheEvict(value = "attributes", allEntries = true)
  public Response<?> update(@NonNull UpdateAttributesRequest request) {

    Attributes attributes = attributesRepository
        .findById(Long.valueOf(request.getId()))
        .orElseThrow(() -> new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND,
            "Thuộc tính sản phẩm không tồn tại."));

    if (request.getName() != null && !request.getName().isBlank()) {
      attributes.setName(request.getName());
    }

    if (request.getPrice() != null) {
      if (request.getPrice() < 0) {
        throw new BusinessException(ErrorCode.INVALID_PRICE, "Giá của sản phẩm thể là số âm.");
      }
      attributes.setPrice(request.getPrice());
    }

    if (request.getSalePrice() != null) {
      if (request.getSalePrice() < 0) {
        throw new BusinessException(ErrorCode.INVALID_PRICE, "Giá khuyến mãi không thể là số âm.");
      }
      double currentPrice = request.getPrice() != null ? request.getPrice() : attributes.getPrice();
      if (request.getSalePrice() > currentPrice) {
        throw new BusinessException(ErrorCode.INVALID_PRICE, "Giá khuyến mãi không thể lớn hơn giá gốc.");
      }
      attributes.setSalePrice(request.getSalePrice());
    }

    if (request.getVariantOptions() != null && !request.getVariantOptions().isEmpty()) {
      List<VariantOption> variantOptions = request.getVariantOptions().stream()
          .map(dto -> new com.ddicg.erp.core.common.model.embedded.VariantOption(dto.getName(), dto.getValues() != null ? dto.getValues() : new java.util.ArrayList<>()))
          .toList();
      attributes.setVariantOptions(variantOptions);
    }

    if (request.getKeywords() != null) {
      attributes.setKeywords(new HashSet<>(request.getKeywords()));
    }

    if (request.getSpecifications() != null) {
      // Specification update skipped for type-safe migration
    }

    if (request.getPromotions() != null) {
      attributes.getPromotions().clear();
      attributes.getPromotions().addAll(
          promotionMapper.toEntity(request.getPromotions()));
    }

    if (request.getStatus() != null) {
      attributes.setStatusProduct(request.getStatus());
    }

    attributes.addUpdateEntry("Cập nhật thuộc tính sản phẩm", securityUtil.getCurrentUsername());

    attributesRepository.save(attributes);

    // Hook: Gửi yêu cầu xóa cache bất đồng bộ qua Redis Stream
    if (attributes.getProduct() != null) {
      redisProducerService.sendEvictMessage(attributes.getProduct().getId().toString());
    }
    SMART_SEARCH_CACHE.invalidateAll();

    return Response.ok("Đã cập nhật thành công.");
  }

  @Override
  @Transactional
  @CacheEvict(value = "attributes", allEntries = true)
  public Response<?> delete(@NonNull List<String> ids) {
    if (ids.isEmpty()) {
      throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Mã định danh không được để trống.");
    }

    List<Long> attrUuids = ids.stream()
        .map(Long::valueOf)
        .toList();

    List<Attributes> attributesToDelete = attributesRepository.findAllById(attrUuids);

    if (attributesToDelete.isEmpty()) {
      throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND,
          "Không tìm thấy Danh mục với mã định danh đã cung cấp.");
    }

    if (attributesToDelete.size() != ids.size()) {
      log.warn("Một số SKU không tồn tại. Yêu cầu: {}, Tìm thấy: {}",
          ids.size(),
          attributesToDelete.size());
    }

    String currentUser = securityUtil.getCurrentUsername();

    // Soft delete
    attributesToDelete.forEach(attr -> {
      attr.markDeletedAfter30Days(currentUser);
    });

    attributesRepository.saveAll(attributesToDelete);

    log.info("Đã xóa {} attributes", attributesToDelete.size());

    // Hook: Gửi yêu cầu xóa cache bất đồng bộ qua Redis Stream cho các sản phẩm
    // liên quan
    attributesToDelete.stream()
        .map(Attributes::getProduct)
        .filter(Objects::nonNull)
        .map(Product::getId)
        .distinct()
        .forEach(productId -> redisProducerService.sendEvictMessage(productId.toString()));
    SMART_SEARCH_CACHE.invalidateAll();

    return Response.noContent();
  }

  @Override
  @Transactional
  @CacheEvict(value = "attributes", allEntries = true)
  public Response<?> deleteByProduct(@NonNull String productId) {
    Product product = productRepository.findById(Long.valueOf(productId))
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại."));

    List<Attributes> attributesList = attributesRepository.findAllByProduct(product);

    if (attributesList.isEmpty()) {
      return Response.ok(null, "Sản phẩm không có danh mục nào để xóa.");
    }

    String currentUser = securityUtil.getCurrentUsername();

    attributesList.forEach(attr -> {
      attr.markDeletedAfter30Days(currentUser);
    });

    attributesRepository.saveAll(attributesList);

    log.info("Đã xóa {} attributes của sản phẩm {} bởi user {}",
        attributesList.size(),
        product.getName(),
        currentUser);

    // Hook: Gửi yêu cầu xóa cache bất đồng bộ qua Redis Stream
    redisProducerService.sendEvictMessage(productId);
    SMART_SEARCH_CACHE.invalidateAll();

    return Response.ok(
        null,
        String.format("Đã xóa thành công %d danh mục của sản phẩm.", attributesList.size()));
  }

  @Override
    @Transactional(readOnly = true)
    public Page<AttributesDto> search(AttributesSearchRequest request) {
        AttributesSearchRequest effectiveRequest = request == null ? new AttributesSearchRequest() : request;
        List<String> productSkuFilters = collectProductSkus(effectiveRequest);
        List<Long> productIdsFromSkus = resolveProductIdsFromSkus(productSkuFilters);
        if (!productSkuFilters.isEmpty()
            && productIdsFromSkus.isEmpty()
            && !hasDirectProductIdFilter(effectiveRequest)
            && filterSkus(effectiveRequest.getSkus()).isEmpty()) {
            var pageable = merchandiseSearchService.pageable(effectiveRequest.getPaging());
            return Page.empty(pageable);
        }

        AttributesSearchRequest resolvedRequest = withResolvedProductIds(effectiveRequest, productIdsFromSkus);
        Page<AttributesDto> smartPage = smartSearchFromDistinguishedCache(resolvedRequest);
        if (smartPage != null) {
            return smartPage;
        }

        List<Long> ids = merchandiseSearchService.searchAttributeIds(resolvedRequest);
        List<AttributesDto> content = getAttributesByIds(ids).getData();

        var pageable = merchandiseSearchService.pageable(resolvedRequest.getPaging());

        long total;
        total = attributesRepository.count(merchandiseSearchService.attributesSpecification(resolvedRequest));

        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

  @Override
  @Transactional(readOnly = true)
  public Response<List<AttributesDto>> getAttributesByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Response.ok(new ArrayList<>());
    }

    Map<Long, AttributesDto> dtoMap = CacheUtils.getAll(
        cacheManager,
        CacheConfig.CACHE_ATTRIBUTES,
        ids,
        missingIds -> attributesRepository.getQuantityAttributesById(new ArrayList<>(missingIds)).stream()
            .collect(Collectors.toMap(Attributes::getId, attributesMapper::toDto)));

    List<AttributesDto> result = ids.stream()
        .map(dtoMap::get)
        .filter(Objects::nonNull)
        .toList();

    return Response.ok(result);
  }

  @Override
  @Transactional(readOnly = true)
  public Response<List<AttributesDto>> getAttributesBySkus(List<String> skus) {
    if (skus == null || skus.isEmpty()) {
      return Response.ok(new ArrayList<>());
    }

    List<Object[]> rows = attributesRepository.findIdsAndSkusBySkus(skus);
    Map<String, Long> skuToIdMap = rows.stream()
        .collect(Collectors.toMap(
            row -> (String) row[1], // Key là SKU
            row -> (Long) row[0], // Value là ID
            (existing, replacement) -> existing));

    List<Long> ids = skus.stream()
        .map(skuToIdMap::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    if (ids.isEmpty()) {
      return Response.ok(new ArrayList<>());
    }

    List<AttributesDto> dtos = getAttributesByIds(ids).getData();

    Map<String, AttributesDto> skuToDtoMap = dtos.stream()
        .filter(dto -> dto.getSku() != null && dto.getSku().getSku() != null)
        .collect(Collectors.toMap(
            dto -> dto.getSku().getSku(),
            dto -> dto,
            (existing, replacement) -> existing));

    return Response.ok(skus.stream()
        .map(skuToDtoMap::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList()));
  }

  @Override
  @Transactional(readOnly = true)
  public Response<List<AttributesDto>> getAttributesByProductSkus(List<String> productSkus) {
    List<String> filters = filterSkus(productSkus);
    if (filters.isEmpty()) {
      return Response.ok(new ArrayList<>());
    }

    List<Long> productIds = resolveProductIdsFromSkus(filters);
    if (productIds.isEmpty()) {
      return Response.ok(new ArrayList<>());
    }

    List<Long> attributeIds = attributesRepository.findActiveIdsByProductIds(productIds);
    return getAttributesByIds(attributeIds);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Long> searchAttributesIds(AttributesSearchRequest request) {
    AttributesSearchRequest effectiveRequest = request == null ? new AttributesSearchRequest() : request;
    List<String> productSkuFilters = collectProductSkus(effectiveRequest);
    List<Long> productIdsFromSkus = resolveProductIdsFromSkus(productSkuFilters);
    if (!productSkuFilters.isEmpty()
        && productIdsFromSkus.isEmpty()
        && !hasDirectProductIdFilter(effectiveRequest)
        && filterSkus(effectiveRequest.getSkus()).isEmpty()) {
      return List.of();
    }
    return merchandiseSearchService.searchAttributeIds(withResolvedProductIds(effectiveRequest, productIdsFromSkus));
  }

  @Override
  @Cacheable(value = "attributes", key = "#productId")
  public List<AttributesDto> getAttributesByProductId(String productId) {
    log.info("Cache miss! Query DB lấy thuộc tính cho sản phẩm ID: {}", productId);
    return attributesRepository
        .findAllByProductIdNotDeleted(Long.valueOf(productId))
        .stream()
        .map(attributesMapper::toDto)
        .toList();
  }

  private List<String> collectProductSkus(AttributesSearchRequest request) {
    List<String> filters = new ArrayList<>();
    if (org.springframework.util.StringUtils.hasText(request.getProductSku())) {
      filters.add(request.getProductSku().trim());
    }
    filters.addAll(filterSkus(request.getProductSkus()));
    return filters.stream().distinct().toList();
  }

  private List<String> filterSkus(List<String> skus) {
    if (skus == null || skus.isEmpty()) {
      return List.of();
    }
    return skus.stream()
        .filter(org.springframework.util.StringUtils::hasText)
        .map(String::trim)
        .distinct()
        .toList();
  }

  private List<Long> resolveProductIdsFromSkus(List<String> productSkus) {
    if (productSkus == null || productSkus.isEmpty()) {
      return List.of();
    }

    Map<String, Long> skuToIdMap = productRepository.findIdsAndSkusBySkus(productSkus).stream()
        .collect(Collectors.toMap(
            row -> (String) row[1],
            row -> (Long) row[0],
            (existing, replacement) -> existing));

    return productSkus.stream()
        .map(skuToIdMap::get)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  private Page<AttributesDto> smartSearchFromDistinguishedCache(AttributesSearchRequest request) {
    if (!hasPreloadFilter(request)) {
      return null;
    }

    var pageable = merchandiseSearchService.pageable(request.getPaging());
    String distinguish = smartSearchDistinguish(request);
    SmartSearchState state = SMART_SEARCH_CACHE.get(distinguish, key -> buildSmartSearchState(request));
    int requiredMatches = Math.toIntExact(pageable.getOffset() + pageable.getPageSize());
    Set<Long> idFilters = parseLongSet(request.getIds(), "ids");
    Set<StockStatus> statusFilters = parseStockStatusSet(request.getStatuses());

    synchronized (state) {
      state.scanUntil(requiredMatches, candidate -> matchesRemainingFilters(candidate, request, idFilters, statusFilters));
      int fromIndex = Math.toIntExact(pageable.getOffset());
      int toIndex = Math.min(requiredMatches, state.matchedIds.size());
      List<AttributesDto> content = fromIndex >= toIndex
          ? List.of()
          : getAttributesByIds(state.matchedIds.subList(fromIndex, toIndex)).getData();

      long total = state.exhausted
          ? state.matchedIds.size()
          : Math.max(requiredMatches + 1L, state.matchedIds.size());

      log.debug(
          "Attributes smart search distinguish={} cursor={}/{} matched={} exhausted={}",
          distinguish,
          state.cursor,
          state.candidates.size(),
          state.matchedIds.size(),
          state.exhausted);

      return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }
  }

  private SmartSearchState buildSmartSearchState(AttributesSearchRequest request) {
    List<Attributes> candidates = attributesRepository.findAll(preloadSpecification(request), sortForSmartSearch(request));
    hydrateAttributeDetails(candidates);

    List<SmartCandidate> snapshots = candidates.stream()
        .filter(attr -> attr.getId() != null)
        .map(attr -> new SmartCandidate(
            attr.getId(),
            attr.getName(),
            attr.getSku() == null ? null : attr.getSku().getSku(),
            attr.getProduct() == null ? null : attr.getProduct().getId(),
            attr.getStatusProduct(),
            attr.getPrice(),
            attr.getSalePrice(),
            attr.getSoldQuantity(),
            attr.getCostPrice(),
            attr.getCreatedBy(),
            attr.getCreatedAt(),
            attr.getUpdatedAt()))
        .distinct()
        .toList();

    log.info("Attributes smart search warmed distinguish={} candidates={}", smartSearchDistinguish(request), snapshots.size());
    return new SmartSearchState(snapshots);
  }

  private void hydrateAttributeDetails(List<Attributes> candidates) {
    org.springframework.cache.Cache cache = cacheManager.getCache(CacheConfig.CACHE_ATTRIBUTES);
    if (cache == null || !(cache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache<?, ?> nativeCache)) {
      return;
    }

    @SuppressWarnings("unchecked")
    com.github.benmanes.caffeine.cache.Cache<Long, AttributesDto> attributesCache =
        (com.github.benmanes.caffeine.cache.Cache<Long, AttributesDto>) nativeCache;

    candidates.stream()
        .filter(attr -> attr.getId() != null)
        .forEach(attr -> attributesCache.put(attr.getId(), attributesMapper.toDto(attr)));
  }

  private Specification<Attributes> preloadSpecification(AttributesSearchRequest request) {
    return (root, query, cb) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
      predicates.add(cb.or(cb.isNull(root.get("isDeleted")), cb.isFalse(root.get("isDeleted"))));
      predicates.add(cb.or(cb.isNull(root.get("deletedAt")), cb.greaterThan(root.get("deletedAt"), java.time.LocalDateTime.now())));

      List<jakarta.persistence.criteria.Predicate> preloadPredicates = new ArrayList<>();
      Set<Long> productIds = productIdFilters(request);
      if (!productIds.isEmpty()) {
        preloadPredicates.add(root.get("product").get("id").in(productIds));
      }

      List<String> skus = filterSkus(request.getSkus());
      if (!skus.isEmpty()) {
        preloadPredicates.add(root.get("sku").get("sku").in(skus));
      }

      if (!preloadPredicates.isEmpty()) {
        predicates.add(cb.or(preloadPredicates.toArray(jakarta.persistence.criteria.Predicate[]::new)));
      }

      return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }

  private Sort sortForSmartSearch(AttributesSearchRequest request) {
    PagingRequest paging = request.getPaging();
    if (paging != null && paging.getOrders() != null && !paging.getOrders().isEmpty()) {
      Sort sort = paging.sortable(paging.getOrders());
      if (sort.isSorted()) {
        return sort;
      }
    }
    return Sort.by(Sort.Order.asc("id"));
  }

  private boolean hasPreloadFilter(AttributesSearchRequest request) {
    return hasDirectProductIdFilter(request)
        || !filterSkus(request.getProductSkus()).isEmpty()
        || StringUtils.hasText(request.getProductSku())
        || !filterSkus(request.getSkus()).isEmpty();
  }

  private boolean hasDirectProductIdFilter(AttributesSearchRequest request) {
    return StringUtils.hasText(request.getProductId()) || !filterSkus(request.getProductIds()).isEmpty();
  }

  private boolean matchesRemainingFilters(
      SmartCandidate candidate,
      AttributesSearchRequest request,
      Set<Long> idFilters,
      Set<StockStatus> statusFilters) {
    if (candidate == null) {
      return false;
    }
    if (!idFilters.isEmpty() && !idFilters.contains(candidate.id())) {
      return false;
    }
    if (!statusFilters.isEmpty() && !statusFilters.contains(candidate.statusProduct())) {
      return false;
    }
    if (StringUtils.hasText(request.getKeyword()) && !containsIgnoreCase(candidate.name(), request.getKeyword())) {
      return false;
    }
    if (StringUtils.hasText(request.getCreatedBy()) && !containsIgnoreCase(candidate.createdBy(), request.getCreatedBy())) {
      return false;
    }
    return inRange(candidate.price(), request.getMinPrice(), request.getMaxPrice())
        && inRange(candidate.salePrice(), request.getMinSalePrice(), request.getMaxSalePrice())
        && inRange(candidate.soldQuantity(), request.getMinSoldQuantity(), request.getMaxSoldQuantity())
        && inRange(candidate.costPrice(), request.getMinCostPrice(), request.getMaxCostPrice())
        && inRange(candidate.createdAt(), request.getCreatedFrom(), request.getCreatedTo())
        && inRange(candidate.updatedAt(), request.getUpdatedFrom(), request.getUpdatedTo());
  }

  private boolean containsIgnoreCase(String actual, String expected) {
    return actual != null && actual.toLowerCase().contains(expected.trim().toLowerCase());
  }

  private <T extends Comparable<T>> boolean inRange(T value, T min, T max) {
    if (value == null) {
      return min == null && max == null;
    }
    return (min == null || value.compareTo(min) >= 0) && (max == null || value.compareTo(max) <= 0);
  }

  private Set<Long> productIdFilters(AttributesSearchRequest request) {
    Set<Long> productIds = parseLongSet(request.getProductIds(), "productIds");
    if (StringUtils.hasText(request.getProductId())) {
      productIds.add(parseLong(request.getProductId(), "productId"));
    }
    return productIds;
  }

  private Set<Long> parseLongSet(List<String> values, String fieldName) {
    List<String> filters = filterSkus(values);
    if (filters.isEmpty()) {
      return new LinkedHashSet<>();
    }
    return filters.stream()
        .map(value -> parseLong(value, fieldName))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Long parseLong(String value, String fieldName) {
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException ex) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, fieldName + " không hợp lệ.");
    }
  }

  private Set<StockStatus> parseStockStatusSet(List<String> values) {
    return filterSkus(values).stream()
        .map(value -> {
          try {
            return StockStatus.valueOf(value.trim().toUpperCase());
          } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "statuses không hợp lệ.");
          }
        })
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private String smartSearchDistinguish(AttributesSearchRequest request) {
    PagingRequest paging = request.getPaging();
    String sortPart = paging == null || paging.getOrders() == null
        ? "{}"
        : paging.getOrders().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(",", "{", "}"));

    return SMART_SEARCH_DISTINGUISH_PREFIX
        + "ids=" + sorted(filterSkus(request.getIds()))
        + "|productId=" + trimToEmpty(request.getProductId())
        + "|productIds=" + sorted(filterSkus(request.getProductIds()))
        + "|productSku=" + trimToEmpty(request.getProductSku())
        + "|productSkus=" + sorted(filterSkus(request.getProductSkus()))
        + "|skus=" + sorted(filterSkus(request.getSkus()))
        + "|statuses=" + sorted(filterSkus(request.getStatuses()))
        + "|keyword=" + trimToEmpty(request.getKeyword()).toLowerCase()
        + "|minPrice=" + request.getMinPrice()
        + "|maxPrice=" + request.getMaxPrice()
        + "|minSalePrice=" + request.getMinSalePrice()
        + "|maxSalePrice=" + request.getMaxSalePrice()
        + "|minSoldQuantity=" + request.getMinSoldQuantity()
        + "|maxSoldQuantity=" + request.getMaxSoldQuantity()
        + "|minCostPrice=" + request.getMinCostPrice()
        + "|maxCostPrice=" + request.getMaxCostPrice()
        + "|createdBy=" + trimToEmpty(request.getCreatedBy()).toLowerCase()
        + "|createdFrom=" + request.getCreatedFrom()
        + "|createdTo=" + request.getCreatedTo()
        + "|updatedFrom=" + request.getUpdatedFrom()
        + "|updatedTo=" + request.getUpdatedTo()
        + "|size=" + (paging == null ? 10 : paging.getSize())
        + "|sort=" + sortPart;
  }

  private List<String> sorted(List<String> values) {
    return values.stream().sorted(Comparator.naturalOrder()).toList();
  }

  private String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private AttributesSearchRequest withResolvedProductIds(
      AttributesSearchRequest request,
      List<Long> productIdsFromSkus) {
    if (productIdsFromSkus == null || productIdsFromSkus.isEmpty()) {
      return request;
    }

    List<String> mergedProductIds = new ArrayList<>();
    if (request.getProductIds() != null) {
      mergedProductIds.addAll(request.getProductIds());
    }
    mergedProductIds.addAll(productIdsFromSkus.stream().map(String::valueOf).toList());

    AttributesSearchRequest resolved = new AttributesSearchRequest();
    resolved.setKeyword(request.getKeyword());
    resolved.setProductId(request.getProductId());
    resolved.setProductSku(request.getProductSku());
    resolved.setIds(request.getIds());
    resolved.setProductIds(mergedProductIds.stream().distinct().toList());
    resolved.setProductSkus(request.getProductSkus());
    resolved.setSkus(request.getSkus());
    resolved.setStatuses(request.getStatuses());
    resolved.setMinPrice(request.getMinPrice());
    resolved.setMaxPrice(request.getMaxPrice());
    resolved.setMinSalePrice(request.getMinSalePrice());
    resolved.setMaxSalePrice(request.getMaxSalePrice());
    resolved.setMinSoldQuantity(request.getMinSoldQuantity());
    resolved.setMaxSoldQuantity(request.getMaxSoldQuantity());
    resolved.setMinCostPrice(request.getMinCostPrice());
    resolved.setMaxCostPrice(request.getMaxCostPrice());
    resolved.setCreatedBy(request.getCreatedBy());
    resolved.setCreatedFrom(request.getCreatedFrom());
    resolved.setCreatedTo(request.getCreatedTo());
    resolved.setUpdatedFrom(request.getUpdatedFrom());
    resolved.setUpdatedTo(request.getUpdatedTo());
    resolved.setPaging(request.getPaging());
    return resolved;
  }

  private record SmartCandidate(
      Long id,
      String name,
      String sku,
      Long productId,
      StockStatus statusProduct,
      Double price,
      Double salePrice,
      Integer soldQuantity,
      Double costPrice,
      String createdBy,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
  }

  private static final class SmartSearchState {
    private final List<SmartCandidate> candidates;
    private final List<Long> matchedIds = new ArrayList<>();
    private int cursor;
    private boolean exhausted;

    private SmartSearchState(List<SmartCandidate> candidates) {
      this.candidates = candidates;
    }

    private void scanUntil(
        int requiredMatches,
        java.util.function.Predicate<SmartCandidate> matcher) {
      while (!exhausted && matchedIds.size() < requiredMatches && cursor < candidates.size()) {
        SmartCandidate candidate = candidates.get(cursor++);
        if (matcher.test(candidate)) {
          matchedIds.add(candidate.id());
        }
      }
      exhausted = cursor >= candidates.size();
    }
  }

}