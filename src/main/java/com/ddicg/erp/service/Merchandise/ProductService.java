package com.ddicg.erp.service.Merchandise;

import com.ddicg.erp.mapper.ProductMapper;
import com.ddicg.erp.model.embedded.SkuInfo;
import com.ddicg.erp.model.entity.Category;
import com.ddicg.erp.model.entity.Product;
import com.ddicg.erp.model.enums.ActiveStatus;
import com.ddicg.erp.repository.CategoryRepository;
import com.ddicg.erp.repository.ProductRepository;
import com.ddicg.erp.service.MinioService;
import com.ddicg.erp.service.dto.CategoryDto;
import com.ddicg.erp.service.dto.ProductDto;
import com.ddicg.erp.service.dto.request.CreateProductRequest;
import com.ddicg.erp.service.dto.request.GetProductRequest;
import com.ddicg.erp.service.dto.request.UpdateProductRequest;
import com.ddicg.erp.service.dto.response.ProductIsExiting;
import com.ddicg.erp.service.dto.response.ResponseConfig.Response;
import com.ddicg.erp.service.RedisProducerService;
import com.ddicg.erp.service.interfaces.iProduct;
import com.ddicg.erp.util.SecurityUtil;
import com.ddicg.erp.web.rest.error.ErrorCode;
import com.ddicg.erp.web.rest.error.BusinessException;
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
    private final com.ddicg.erp.caffeine_cache.CacheSyncService cacheSyncService;
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
        if (!StringUtils.hasText(request.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Sản phẩm không không được để trống.");
        }

        final var product = productRepository.findById(Long.valueOf(request.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại."));

        if (StringUtils.hasText(request.getCategoryId())) {
            Category category = categoryRepository
                    .findCategoryById(Long.valueOf(request.getCategoryId()))
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
    public Response<?> deleteProduct(@NonNull final List<Long> ids) {
        // Xóa mềm danh sách sản phẩm
        productRepository.softDeleteAllByIds(ids, securityUtil.getCurrentUsername());
        // Hook: Gửi yêu cầu xóa cache bất đồng bộ qua Redis Stream
        ids.forEach(id -> redisProducerService.sendEvictMessage(id.toString()));
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
    public void viewCount(String productId) {
        productRepository.updateViewCount(Long.valueOf(productId));
    }

    @Override
    public void totalSoldQuantity(String productId) {
        productRepository.updateTotalSoldQuantity(
                Long.valueOf(productId),
                1);
    }

    @Override
    public void totalRevenue(String productId, double price) {
        productRepository.updateTotalRevenue(
                Long.valueOf(productId),
                BigDecimal.valueOf(price));
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
