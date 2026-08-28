package com.ddicg.erp.modules.merchandise.service;

import com.ddicg.erp.core.common.model.enums.CachingStatus;
import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.modules.merchandise.dto.ProductCachingDto;
import com.ddicg.erp.modules.merchandise.dto.ProductDto;
import com.ddicg.erp.modules.merchandise.mapper.ProductMapper;
import com.ddicg.erp.modules.merchandise.model.Product;
import com.ddicg.erp.modules.merchandise.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCachingService implements iProductCaching {

    private final RedisService redisService;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    public void addProduct(List<ProductDto> items) {

        String recommendationId = UUID.randomUUID().toString();

        Set<Long> productIds = items.stream()
                .map(ProductDto::getId)
                .collect(Collectors.toSet());

        List<Product> products = productRepository.findAllById(productIds);

        if (products.size() != productIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại.");
        }

        ProductCachingDto productCachingDto = ProductCachingDto.builder()
                .recommendationId(recommendationId)
                .strategy(String.valueOf(CachingStatus.PENDING))
                .items(productMapper.toDto(products))
                .generatedAt(System.currentTimeMillis())
                .build();

        // Áp dụng TTL 1 giờ + Random Jitter chống Cache Avalanche qua RedisTable
        redisService.setValueWithJitter(RedisTable.CATALOG_REC, recommendationId, productCachingDto, 3600, 120, TimeUnit.SECONDS);
    }
}
