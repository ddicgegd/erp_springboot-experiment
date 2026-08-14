package com.ddicg.erp.modules.merchandise.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.modules.merchandise.mapper.ProductMapper;
import com.ddicg.erp.modules.merchandise.model.Product;
import com.ddicg.erp.core.common.model.enums.CachingStatus;
import com.ddicg.erp.modules.merchandise.repository.ProductRepository;
import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.modules.merchandise.dto.ProductCachingDto;
import com.ddicg.erp.modules.merchandise.dto.ProductDto;
import com.ddicg.erp.modules.merchandise.service.iProductCaching;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
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

        String key = "rec:" + recommendationId;

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

        redisService.setValue(key, productCachingDto);
    }
}