package com.ddicg.erp.modules.merchandise.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.modules.merchandise.mapper.ProductMapper;
import com.ddicg.erp.core.common.model.embedded.MediaItem;
import com.ddicg.erp.modules.merchandise.model.Product;
import com.ddicg.erp.modules.merchandise.repository.ProductRepository;
import com.ddicg.erp.core.common.service.MinioService;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.merchandise.service.iProductImage;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService implements iProductImage {


    private final ProductRepository productRepository;
    private final MinioService minioService;
    private final SecurityUtil securityUtil;
    private final Helper featureMerchandiseHelper;
    private final ProductMapper productMapper;

    private List<MediaItem> uploadImages(List<MultipartFile> images) {
        List<MediaItem> mediaItems = new ArrayList<>();
        List<String> uploadedUrls = new ArrayList<>();

        try {
            for (MultipartFile file : images) {
                if (file.isEmpty())
                    continue;

                String url = minioService.uploadFile(file);
                uploadedUrls.add(url);

                String key = featureMerchandiseHelper.generateKey();
                mediaItems.add(MediaItem.builder()
                        .key(key)
                        .url(url)
                        .build());
            }
            return mediaItems;

        } catch (Exception e) {
            // Rollback
            for (String url : uploadedUrls) {
                try {
                    minioService.deleteFile(url);
                } catch (Exception deleteEx) {
                    log.error("Không thể xóa file {} sau khi rollback: {}", url, deleteEx.getMessage());
                }
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Lỗi khi upload file: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Response<?> addProductImages(String productId, List<MultipartFile> images) {
        final var product = productRepository.findById(Long.valueOf(productId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại."));

        if (CollectionUtils.isEmpty(images)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Ảnh không được để trống.");
        }

        List<MediaItem> newItems = uploadImages(images);
        product.getMediaItems().addAll(newItems);
        product.addUpdateEntry("Thêm " + newItems.size() + " ảnh sản phẩm",
                securityUtil.getCurrentUsername());
        log.info("Đã thêm {} ảnh mới vào sản phẩm {}", newItems.size(), productId);

        return Response.ok(productRepository.save(product), "Thêm ảnh thành công.");
    }

    @Override
    @Transactional
    public Response<?> deleteProductImage(String productId, String imageKey) {
        final var product = productRepository.findById(Long.valueOf(productId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại."));

        MediaItem itemToDelete = product.getMediaItems().stream()
                .filter(mediaItem -> mediaItem.getKey().equals(imageKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,
                        "Không tìm thấy ảnh với key: " + imageKey));

        try {
            minioService.deleteFile(itemToDelete.getUrl());
        } catch (Exception e) {
            log.error("Lỗi khi xóa file trên MinIO: {}", e.getMessage());
        }

        product.getMediaItems().remove(itemToDelete);
        product.addUpdateEntry("Xóa ảnh sản phẩm: " + imageKey, securityUtil.getCurrentUsername());

        log.info("Đã xóa ảnh {} khỏi sản phẩm {}", imageKey, productId);

        Product savedProduct = productRepository.save(product);
        return Response.ok(productMapper.toDto(savedProduct), "Xóa ảnh thành công.");
    }

    @Override
    @Transactional
    public Response<?> replaceProductImages(String productId, List<MultipartFile> images) {
        Long uuid;
        try {
            uuid = Long.valueOf(productId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "ID sản phẩm không hợp lệ.");
        }

        final var product = productRepository.findById(uuid)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại."));

        if (CollectionUtils.isEmpty(images)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Ảnh không được để trống.");
        }

        for (MediaItem oldItem : product.getMediaItems()) {
            try {
                minioService.deleteFile(oldItem.getUrl());
            } catch (Exception e) {
                log.error("Lỗi khi xóa file cũ {}: {}", oldItem.getUrl(), e.getMessage());
            }
        }

        product.getMediaItems().clear();

        List<MediaItem> newItems = uploadImages(images);
        product.getMediaItems().addAll(newItems);
        product.addUpdateEntry("Thay thế " + newItems.size() + " ảnh sản phẩm",
                securityUtil.getCurrentUsername());
        log.info("Đã thay thế {} ảnh cho sản phẩm {}", newItems.size(), productId);

        Product savedProduct = productRepository.save(product);
        return Response.ok(productMapper.toDto(savedProduct), "Thay thế ảnh thành công.");
    }

    @Override
    public byte[] getProductImage(String imageName) {
        try (java.io.InputStream inputStream = minioService.getFile(imageName)) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            log.error("Lỗi khi đọc ảnh {}: {}", imageName, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không thể đọc dữ liệu ảnh: " + imageName);
        }
    }
}