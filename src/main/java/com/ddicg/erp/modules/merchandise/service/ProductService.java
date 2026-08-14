package com.ddicg.erp.modules.merchandise.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.modules.merchandise.mapper.ProductMapper;
import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.modules.merchandise.model.Category;
import com.ddicg.erp.modules.merchandise.model.Product;
import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.modules.merchandise.repository.CategoryRepository;
import com.ddicg.erp.modules.merchandise.repository.ProductRepository;
import com.ddicg.erp.core.common.service.MinioService;
import com.ddicg.erp.modules.merchandise.dto.CategoryDto;
import com.ddicg.erp.modules.merchandise.dto.ProductDto;
import com.ddicg.erp.modules.merchandise.dto.request.CreateProductRequest;
import com.ddicg.erp.modules.merchandise.dto.request.GetProductRequest;
import com.ddicg.erp.modules.merchandise.dto.request.UpdateProductRequest;
import com.ddicg.erp.modules.merchandise.dto.response.ProductIsExiting;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.service.RedisProducerService;
import com.ddicg.erp.modules.merchandise.service.iProduct;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.exception.BusinessException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService implements iProduct {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SecurityUtil securityUtil;
    private final MinioService minioService;
    private final ProductMapper productMapper;
    private final com.ddicg.erp.core.config.cache.CacheSyncService cacheSyncService;
    private final org.springframework.cache.CacheManager cacheManager;
    private final RedisProducerService redisProducerService;
    private final MerchandiseSearchService merchandiseSearchService;
    private final CategoryService categoryService;

    @Override
    public Response<?> addProduct(CreateProductRequest request) {
        Category category = categoryRepository
                .findCategoryBySkuInfo_Sku(request.getCategorySku())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "Danh mục không tồn tại."));

        productRepository.save(
                Product.builder()
                        .name(request.getName())
                        .category(category)
                        .skuInfo(SkuInfo.builder()
                                .sku(new SkuInfo().createSku("prd-").getSku()
                                        .replaceFirst("-",
                                                "-" + request.getCategorySku()
                                                        .substring(request.getCategorySku().length() - 2)))
                                .build())
                        .createdAt(LocalDateTime.now())
                        .createdBy(securityUtil.getCurrentUsername())
                        .status(Stream.of(ActiveStatus.ACTIVE, ActiveStatus.LOCKED)
                                .filter(s -> s.name().equals(request.getStatus()))
                                .findFirst()
                                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                                        "Định dạng không hợp lệ!")))
                        .build());
        return Response.ok("Thêm sản phẩm '" + request.getName() + "' thành công.");
    }

    @Override
    @Transactional
    public Response<?> updateProduct(UpdateProductRequest request) {
        if (!StringUtils.hasText(request.getSku())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Sản phẩm không không được để trống.");
        }

        final var product = productRepository.findProductBySkuInfo_Sku(request.getSku())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại."));

        if (StringUtils.hasText(request.getCategorySku())) {
            Category category = categoryRepository
                    .findCategoryBySkuInfo_Sku(request.getCategorySku())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "Danh mục không tồn tại."));
            product.setCategory(category);
        }

        productMapper.updateFromRequest(request, product);

        product.addUpdateEntry("Cập nhật thông tin sản phẩm", securityUtil.getCurrentUsername());

        log.info("Đã cập nhật sản phẩm '{}' với ID {}", product.getName(), product.getId());
        productRepository.save(product);

        // Hook: Gửi yêu cầu xóa cache bất đồng bộ qua Redis Stream
        redisProducerService.sendEvictMessage(product.getId().toString());

        // Hook: Báo hiệu cho luồng đồng bộ chạy ngầm cập nhật cache RAM
        cacheSyncService.markProductDirty(product.getId());

        return Response.ok("Cập nhật sản phẩm thành công.");
    }

    @Override
    @CacheEvict(value = "productDetails", allEntries = true)
    public Response<?> delete(@NonNull final List<String> skus) {
        if (skus.isEmpty()) return Response.noContent();
        
        List<Object[]> rows = productRepository.findIdsAndSkusBySkus(skus);
        List<Long> ids = rows.stream().map(row -> (Long) row[0]).toList();
        
        if (!ids.isEmpty()) {
            productRepository.softDeleteAllByIds(ids, securityUtil.getCurrentUsername());
            ids.forEach(id -> redisProducerService.sendEvictMessage(id.toString()));
        }
        return Response.noContent();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductDto> searchProducts(@NonNull GetProductRequest request) {
        var pageable = merchandiseSearchService.pageable(request.getPaging());
        List<String> categorySkuFilters = collectCategorySkus(request);
        List<Long> categoryIdsFromSkus = resolveCategoryIdsFromSkus(categorySkuFilters);
        if (!categorySkuFilters.isEmpty() && categoryIdsFromSkus.isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<Product> specification = merchandiseSearchService.productSpecification(request);
        if (!categoryIdsFromSkus.isEmpty()) {
            specification = specification.and(categoryIdIn(categoryIdsFromSkus));
        }

        return productRepository.findAll(specification, pageable)
                .map(productMapper::toDto);
    }

    @Override
    public ProductIsExiting isExiting(String name) {
        return productRepository.findProductByName(name)
                .map(p -> ProductIsExiting
                        .builder()
                        .id(String.valueOf(p.getId()))
                        .isExiting(true)
                        .build())
                .orElseGet(() -> ProductIsExiting
                        .builder()
                        .id(null)
                        .isExiting(false)
                        .build());
    }

    // Lazy Load: Lấy chi tiết sản phẩm theo ID từ cache RAM.
    // Lần đầu gọi (Cache Miss) sẽ query DB bằng JOIN FETCH để tải Product + Category trong 1 câu SQL rồi lưu vào RAM. Những lần sau lấy thẳng từ RAM.
    @Cacheable(value = "productDetails", key = "#id")
    public ProductDto getProductById(Long id) {
        log.info("Cache miss! Query DB lấy thông tin sản phẩm ID: {}", id);
        return productRepository.findByIdWithDetails(id)
                .map(productMapper::toDto)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại."));
    }

    @Override
    public void viewCount(String sku) {
        Long id = productRepository.findIdBySku(sku)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại."));
        productRepository.updateViewCount(id);
    }

    @Override
    public void totalSoldQuantity(String sku) {
        Long id = productRepository.findIdBySku(sku)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại."));
        productRepository.updateTotalSoldQuantity(id, 1);
    }

    @Override
    public void totalRevenue(String sku, double price) {
        Long id = productRepository.findIdBySku(sku)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại."));
        productRepository.updateTotalRevenue(id, BigDecimal.valueOf(price));
    }

    @Override
    @Transactional(readOnly = true)
    public Response<List<ProductDto>> getProductsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Response.ok(new java.util.ArrayList<>());
        }

        org.springframework.cache.Cache cache = cacheManager.getCache("productDetails");
        java.util.Map<Long, ProductDto> dtoMap;

        if (cache != null) {
            @SuppressWarnings("unchecked")
            com.github.benmanes.caffeine.cache.Cache<Long, ProductDto> nativeCache =
                    (com.github.benmanes.caffeine.cache.Cache<Long, ProductDto>) cache.getNativeCache();

            dtoMap = nativeCache.getAll(ids, missingIds -> {
                List<Long> missingList = new java.util.ArrayList<>(missingIds);
                List<Product> products = productRepository.findActiveByIdIn(missingList);
                java.util.Map<Long, ProductDto> loaded = new java.util.HashMap<>();
                for (Product p : products) {
                    loaded.put(p.getId(), productMapper.toDto(p));
                }
                return loaded;
            });
        } else {
            List<Product> products = productRepository.findActiveByIdIn(ids);
            dtoMap = new java.util.HashMap<>();
            for (Product p : products) {
                dtoMap.put(p.getId(), productMapper.toDto(p));
            }
        }

        // Reconstruct list preserving the original requested order
        List<ProductDto> result = new java.util.ArrayList<>();
        for (Long id : ids) {
            ProductDto dto = dtoMap.get(id);
            if (dto != null) {
                result.add(dto);
            }
        }

        return Response.ok(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Response<List<ProductDto>> getProductsBySkus(List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return Response.ok(new java.util.ArrayList<>());
        }

        List<Object[]> rows = productRepository.findIdsAndSkusBySkus(skus);
        java.util.Map<String, Long> skuToIdMap = new java.util.HashMap<>();
        for (Object[] row : rows) {
            Long id = (Long) row[0];
            String s = (String) row[1];
            skuToIdMap.put(s, id);
        }

        List<Long> ids = new java.util.ArrayList<>();
        for (String s : skus) {
            Long id = skuToIdMap.get(s);
            if (id != null) {
                ids.add(id);
            }
        }

        List<ProductDto> dtos = getProductsByIds(ids).getData();

        java.util.Map<String, ProductDto> skuToDtoMap = new java.util.HashMap<>();
        for (ProductDto dto : dtos) {
            if (dto.getSkuInfo() != null && dto.getSkuInfo().getSku() != null) {
                skuToDtoMap.put(dto.getSkuInfo().getSku(), dto);
            }
        }

        List<ProductDto> result = new java.util.ArrayList<>();
        for (String s : skus) {
            ProductDto dto = skuToDtoMap.get(s);
            if (dto != null) {
                result.add(dto);
            }
        }

        return Response.ok(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Response<List<ProductDto>> getProductsByCategorySkus(List<String> categorySkus) {
        List<String> filters = filterSkus(categorySkus);
        if (filters.isEmpty()) {
            return Response.ok(new java.util.ArrayList<>());
        }

        List<Long> categoryIds = resolveCategoryIdsFromSkus(filters);
        if (categoryIds.isEmpty()) {
            return Response.ok(new java.util.ArrayList<>());
        }

        List<Long> productIds = productRepository.findActiveIdsByCategoryIds(categoryIds);
        return getProductsByIds(productIds);
    }

    private List<String> collectCategorySkus(GetProductRequest request) {
        List<String> filters = new java.util.ArrayList<>();
        if (StringUtils.hasText(request.getCategorySku())) {
            filters.add(request.getCategorySku().trim());
        }
        filters.addAll(filterSkus(request.getCategorySkus()));
        return filters.stream().distinct().toList();
    }

    private List<String> filterSkus(List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return java.util.List.of();
        }
        return skus.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<Long> resolveCategoryIdsFromSkus(List<String> categorySkus) {
        if (categorySkus == null || categorySkus.isEmpty()) {
            return java.util.List.of();
        }
        return categoryService.getCategoriesBySkus(categorySkus).getData().stream()
                .map(CategoryDto::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private Specification<Product> categoryIdIn(List<Long> categoryIds) {
        return (root, query, cb) -> root.get("category").get("id").in(categoryIds);
    }

}