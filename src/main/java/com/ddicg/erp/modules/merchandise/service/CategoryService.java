package com.ddicg.erp.modules.merchandise.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.modules.merchandise.mapper.CategoryMapper;
import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.modules.merchandise.model.Category;
import com.ddicg.erp.modules.merchandise.repository.CategoryRepository;
import com.ddicg.erp.modules.merchandise.dto.CategoryDto;
import com.ddicg.erp.modules.merchandise.dto.request.CategorySearchRequest;
import com.ddicg.erp.modules.merchandise.dto.request.UpdateCategoryRequest;
import com.ddicg.erp.modules.merchandise.dto.response.CategoryExitingResponse;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.merchandise.service.iCategory;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.core.config.cache.CacheConfig;
import com.ddicg.erp.core.config.cache.CacheUtils;
import java.util.Map;
import java.util.Objects;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import org.springframework.transaction.annotation.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService implements iCategory {


    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final SecurityUtil securityUtil;
    private final org.springframework.cache.CacheManager cacheManager;
    private final MerchandiseSearchService merchandiseSearchService;

    @Override
    @CacheEvict(value = "categoryDetails", allEntries = true)
    public Response<?> create(@NonNull final String name) {
        if (categoryRepository.existsAllByName(name)) {
            throw new BusinessException(ErrorCode.CATEGORY_ALREADY_EXISTS, "Danh mục đã tồn tại.");
        }
        
        categoryRepository.save(
                Category.builder()
                        .name(name)
                        .skuInfo(new SkuInfo().createSku("ctgr-"))
                        .createdBy(securityUtil.getCurrentUsername())
                        .createdAt(LocalDateTime.now())
                        .build()
        );
        log.info("Đã tạo mới danh mục {}", name);
        
        return Response.ok("Tạo danh mục thành công.");
    }

    @Override
    @Transactional
    @CacheEvict(value = "categoryDetails", allEntries = true)
    public Response<?> update(final UpdateCategoryRequest request) {
        Optional<Category> optionalCategory = categoryRepository
                .findCategoryBySkuInfo_Sku(request.getSku());
        Category category = optionalCategory
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "Danh mục không tồn tại."));
        category.setName(request.getName());
        category.addUpdateEntry("Cập nhật tên danh mục", securityUtil.getCurrentUsername());
        log.info("Đã sửa danh mục thành {} với mã id {}", category.getName(), category.getId());
        categoryMapper.toDto(categoryRepository.save(category));
        return Response.ok("Sửa danh mục thành công.");
    }

    @Override
    @CacheEvict(value = "categoryDetails", allEntries = true)
    public Response<?> delete(@NonNull final List<String> skus) {
        if (skus.isEmpty()) return Response.noContent();
        
        List<Object[]> rows = categoryRepository.findIdsAndSkusBySkus(skus);
        List<Long> idList = rows.stream().map(row -> (Long) row[0]).collect(Collectors.toList());
        
        if (!idList.isEmpty()) {
            categoryRepository.softDeleteAllByIds(idList, securityUtil.getCurrentUsername());
        }
        // phần get Category sẽ check và tự động xóa nếu quá 30 ngày có yêu cầu xóa
        return Response.noContent();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CategoryDto> search(@NonNull final CategorySearchRequest request) {
        return categoryRepository.findAll(
                merchandiseSearchService.categorySpecification(request),
                merchandiseSearchService.pageable(request.getPaging())).map(categoryMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Response<List<CategoryDto>> getCategoriesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Response.ok(new java.util.ArrayList<>());
        }

        Map<Long, CategoryDto> dtoMap = CacheUtils.getAll(
                cacheManager,
                CacheConfig.CACHE_CATEGORY_DETAILS,
                ids,
                missingIds -> categoryRepository.findActiveByIdIn(new ArrayList<>(missingIds)).stream()
                        .collect(Collectors.toMap(Category::getId, categoryMapper::toDto))
        );

        List<CategoryDto> result = ids.stream()
                .map(dtoMap::get)
                .filter(Objects::nonNull)
                .toList();

        return Response.ok(result);
    }

    @Override
    public CategoryExitingResponse isExiting(String name) {
        return categoryRepository.findCategoryByName(name)
                .map(c -> CategoryExitingResponse.builder()
                        .id(String.valueOf(c.getId()))
                        .isExiting(true)
                        .build())
                .orElseGet(() -> CategoryExitingResponse.builder()
                        .id(null)
                        .isExiting(false)
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Response<List<CategoryDto>> getCategoriesBySkus(List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return Response.ok(new java.util.ArrayList<>());
        }

        List<Object[]> rows = categoryRepository.findIdsAndSkusBySkus(skus);
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

        List<CategoryDto> dtos = getCategoriesByIds(ids).getData();

        java.util.Map<String, CategoryDto> skuToDtoMap = new java.util.HashMap<>();
        for (CategoryDto dto : dtos) {
            if (dto.getSkuInfo() != null && dto.getSkuInfo().getSku() != null) {
                skuToDtoMap.put(dto.getSkuInfo().getSku(), dto);
            }
        }

        List<CategoryDto> result = new java.util.ArrayList<>();
        for (String s : skus) {
            CategoryDto dto = skuToDtoMap.get(s);
            if (dto != null) {
                result.add(dto);
            }
        }

        return Response.ok(result);
    }
}