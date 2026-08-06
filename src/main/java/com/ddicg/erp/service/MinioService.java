
package com.ddicg.erp.service;

import io.minio.*;
import io.minio.errors.MinioException;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final String BUCKET_NAME = "images";
    private volatile boolean bucketInitialized = false;

    private void ensureBucketExists() {
        if (!bucketInitialized) {
            synchronized (this) {
                if (!bucketInitialized) {
                    try {
                        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build());
                        if (!found) {
                            log.info("Đã tạo mới Bucket {}", BUCKET_NAME);
                            minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
                        }
                        bucketInitialized = true;
                    } catch (Exception e) {
                        log.error("Không thể kiểm tra hoặc tạo bucket '{}': {}", BUCKET_NAME, e.getMessage());
                        throw new RuntimeException("MinIO service is unavailable: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    @PostConstruct
    public void initBucket() {
        try {
            ensureBucketExists();
        } catch (Exception e) {
            log.warn("MinIO chưa sẵn sàng lúc khởi động. Ứng dụng vẫn tiếp tục khởi chạy. Chi tiết: {}", e.getMessage());
        }
    }

    /**
     * Upload một file lên MinIO
     *
     * @param file File ảnh cần upload (từ request)
     * @return Tên file đã được lưu trên MinIO
     */
    public String uploadFile(@NonNull final MultipartFile file) throws IOException {
        ensureBucketExists();
        try {
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String objectName = UUID.randomUUID() + fileExtension;

            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(BUCKET_NAME)
                                .object(objectName)
                                .stream(inputStream, file.getSize(), - 1)
                                .contentType(file.getContentType())
                                .build()
                );
            }

            return objectName;

        } catch (Exception e) {
            throw new RuntimeException("Có lỗi khi tải tệp lên MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * Lấy file từ MinIO bằng tên file
     *
     * @param fileName Tên file cần lấy (URL đã lưu trong DB)
     * @return InputStream của file
     */
    public InputStream getFile(@NonNull final String fileName) {
        ensureBucketExists();
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(fileName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Có lỗi khi lấy tệp từ MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * Lấy URL tạm thời để xem ảnh (presigned URL)
     * URL này sẽ hết hạn sau thời gian quy định
     *
     * @param fileName Tên file cần lấy URL
     * @param expiryInSeconds Thời gian hết hạn (giây), mặc định 7 ngày
     * @return URL tạm thời
     */
    public String getPresignedUrl(@NonNull final String fileName, int expiryInSeconds) {
        ensureBucketExists();
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(BUCKET_NAME)
                            .object(fileName)
                            .expiry(expiryInSeconds)
                            .build()
            );
        } catch (MinioException | IOException e) {
            throw new RuntimeException("Có lỗi khi tạo URL tạm thời: " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Lấy URL tạm thời với thời gian hết hạn mặc định (7 ngày)
     */
    public String getPresignedUrl(@NonNull final String fileName) {
        return getPresignedUrl(fileName, 7 * 24 * 60 * 60); // 7 ngày
    }

    /**
     * Xóa file từ MinIO
     *
     * @param fileName Tên file cần xóa
     */
    public void deleteFile(@NonNull final String fileName) {
        ensureBucketExists();
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(fileName)
                            .build()
            );
            log.info("Đã xóa file {} từ MinIO", fileName);
        } catch (Exception e) {
            throw new RuntimeException("Có lỗi khi xóa tệp từ MinIO: " + e.getMessage(), e);
        }
    }
}
