package com.ddicg.erp.modules.bookmark.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.bookmark.dto.request.AddBookmarkItemRequest;
import com.ddicg.erp.modules.bookmark.dto.request.StageBookmarkRequest;
import com.ddicg.erp.modules.bookmark.dto.response.BookmarkDetailResponse;
import com.ddicg.erp.modules.bookmark.dto.response.BookmarkItemResponse;
import com.ddicg.erp.modules.bookmark.model.BookmarkContext;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.merchandise.model.Product;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BookmarkServiceImpl implements BookmarkService {

    public static final long SAVED_TTL_DAYS = 7;
    public static final long STAGING_TTL_HOURS = 1;

    RedisService redisService;
    AttributesRepository attributesRepository;
    UserRepository userRepository;
    SecurityUtil securityUtil;

    private Optional<User> findAuthenticatedUser() {
        String username = securityUtil.getCurrentUsername();
        if (username == null || "anonymous".equalsIgnoreCase(username) || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByNameOrEmail(username);
    }

    private BookmarkContext resolveContext(String guestId, String mainSku) {
        if (mainSku == null || mainSku.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Mã sản phẩm chính (mainSku) không được để trống");
        }

        Optional<User> userOpt = findAuthenticatedUser();
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String ownerId = user.getId() != null ? String.valueOf(user.getId()) : user.getUsername();
            return BookmarkContext.builder()
                    .ownerId(ownerId)
                    .mainSku(mainSku.trim())
                    .isGuest(false)
                    .build();
        }

        if (guestId != null && !guestId.isBlank()) {
            return BookmarkContext.builder()
                    .ownerId("guest:" + guestId.trim())
                    .mainSku(mainSku.trim())
                    .isGuest(true)
                    .build();
        }

        throw new BusinessException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập hoặc cung cấp header X-Guest-Id");
    }

    private void validateMainSkuExists(String mainSku) {
        if (mainSku == null || mainSku.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Mã sản phẩm chính (mainSku) không được để trống");
        }
        if (!attributesRepository.existsBySku_sku(mainSku.trim())) {
            throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND,
                    "Sản phẩm chính [" + mainSku + "] không tồn tại trong hệ thống");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Response<BookmarkDetailResponse> getBookmark(String mainSku, String guestId) {
        validateMainSkuExists(mainSku);
        BookmarkContext ctx = resolveContext(guestId, mainSku);
        BookmarkDetailResponse response = getBookmarkDetailFromKey(ctx.getSavedKey(), ctx.getMainSku());
        if (response == null || response.getTotalItems() == null || response.getTotalItems() == 0) {
            BookmarkDetailResponse stagingResponse = getBookmarkDetailFromKey(ctx.getStagingKey(), ctx.getMainSku());
            if (stagingResponse != null && stagingResponse.getTotalItems() != null && stagingResponse.getTotalItems() > 0) {
                return Response.ok(stagingResponse);
            }
        }
        return Response.ok(response);
    }

    @Override
    @Transactional
    public Response<BookmarkItemResponse> addItem(String mainSku, AddBookmarkItemRequest request, String guestId) {
        validateMainSkuExists(mainSku);
        if (request == null || request.getSku() == null || request.getSku().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "SKU phụ kiện không được để trống");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Số lượng phụ kiện phải lớn hơn 0");
        }

        BookmarkContext ctx = resolveContext(guestId, mainSku);
        String subSku = request.getSku().trim();

        // 1. Validate subSku in Database
        Attributes attr = attributesRepository.findAttributesBySku_sku(subSku)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND,
                        "Phụ kiện [" + subSku + "] không tồn tại trong hệ thống"));
        if (attr.getStatusProduct() != StockStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.ATTRIBUTES_OUT_OF_STOCK,
                    "Phụ kiện [" + subSku + "] hiện không khả dụng (" + attr.getStatusProduct().getValue() + ")");
        }

        // 2. Atomic increment in Redis Hash
        String key = ctx.getSavedKey();
        Long currentTtl = redisService.getExpireSeconds(key);
        Long newQty = redisService.hIncrBy(key, subSku, request.getQuantity());

        // 3. KHÔNG RESET TIME: Chỉ đặt TTL 7 ngày (604,800s) khi bookmark được tạo lần đầu
        if (currentTtl == null || currentTtl <= 0) {
            redisService.expire(key, SAVED_TTL_DAYS, TimeUnit.DAYS);
        }

        // 4. Build response item
        Product product = attr != null ? attr.getProduct() : null;
        String productName = product != null ? product.getName() : (attr != null ? attr.getName() : subSku);
        String imageUrl = (product != null && product.getMediaItems() != null && !product.getMediaItems().isEmpty())
                ? product.getMediaItems().get(0).getUrl()
                : "https://images.unsplash.com/photo-1546868871-7041f2a55e12?q=80&w=400&auto=format&fit=crop";

        double unitPrice = attr != null ? attr.getPrice() : 250000.0;
        double salePrice = (attr != null && attr.getSalePrice() > 0) ? attr.getSalePrice() : (attr != null ? attr.getPrice() : 200000.0);
        double subTotal = salePrice * newQty;

        BookmarkItemResponse itemResponse = BookmarkItemResponse.builder()
                .sku(subSku)
                .productName(productName)
                .imageUrl(imageUrl)
                .attributesTitle(attr != null ? attr.getName() : "Phụ kiện đi kèm")
                .unitPrice(unitPrice)
                .salePrice(salePrice)
                .quantity(newQty.intValue())
                .subTotal(subTotal)
                .isAvailable(true)
                .stock(999)
                .build();

        log.info("User click [+] added accessory [{}] to bookmark of main [{}] (Owner: {}, NewQty: {})",
                subSku, ctx.getMainSku(), ctx.getOwnerId(), newQty);

        return Response.ok(itemResponse, "Thêm phụ kiện vào bookmark thành công");
    }

    @Override
    @Transactional
    public Response<BookmarkDetailResponse> stageItems(String mainSku, StageBookmarkRequest request, String guestId) {
        validateMainSkuExists(mainSku);
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Danh sách sản phẩm chuẩn bị không được rỗng");
        }

        BookmarkContext ctx = resolveContext(guestId, mainSku);
        String stagingKey = ctx.getStagingKey();
        Long currentTtl = redisService.getExpireSeconds(stagingKey);

        // Xóa staging cũ trước khi nạp bộ staged items mới nhất
        redisService.unlink(stagingKey);

        for (AddBookmarkItemRequest item : request.getItems()) {
            if (item.getSku() != null && item.getQuantity() != null && item.getQuantity() > 0) {
                redisService.hSet(stagingKey, item.getSku().trim(), String.valueOf(item.getQuantity()));
            }
        }

        // KHÔNG RESET TIME: Giữ nguyên số giây còn lại nếu đã có, chỉ đặt 1h khi tạo mới
        long ttlToSet = (currentTtl != null && currentTtl > 0) ? currentTtl : TimeUnit.HOURS.toSeconds(STAGING_TTL_HOURS);
        redisService.expire(stagingKey, ttlToSet, TimeUnit.SECONDS);

        log.debug("Auto-saved staging items for main [{}] (Owner: {}, Items: {})",
                ctx.getMainSku(), ctx.getOwnerId(), request.getItems().size());

        BookmarkDetailResponse detail = getBookmarkDetailFromKey(stagingKey, ctx.getMainSku());
        return Response.ok(detail, "Tự động lưu danh sách chuẩn bị thành công");
    }

    @Override
    @Transactional
    public Response<BookmarkDetailResponse> persistBookmark(String mainSku, String guestId) {
        validateMainSkuExists(mainSku);
        BookmarkContext ctx = resolveContext(guestId, mainSku);
        String stagingKey = ctx.getStagingKey();
        String savedKey = ctx.getSavedKey();
        Map<Object, Object> staged = redisService.hGetAll(stagingKey);
        if (staged != null && !staged.isEmpty()) {
            for (Map.Entry<Object, Object> entry : staged.entrySet()) {
                redisService.hSet(savedKey, entry.getKey().toString(), entry.getValue().toString());
            }
            redisService.unlink(stagingKey);
        }
        redisService.expire(savedKey, SAVED_TTL_DAYS, TimeUnit.DAYS);
        BookmarkDetailResponse response = getBookmarkDetailFromKey(savedKey, ctx.getMainSku());
        return Response.ok(response, "Đã lưu bookmark vào hệ thống trong 7 ngày");
    }

    @Override
    @Transactional
    public Response<Void> removeItem(String mainSku, String sku, String guestId) {
        if (sku == null || sku.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "SKU không được để trống");
        }
        BookmarkContext ctx = resolveContext(guestId, mainSku);
        redisService.hDelete(ctx.getSavedKey(), sku.trim());
        redisService.hDelete(ctx.getStagingKey(), sku.trim());

        log.info("Owner [{}] removed accessory [{}] from bookmark [{}]", ctx.getOwnerId(), sku, ctx.getMainSku());
        return Response.ok(null, "Đã xóa phụ kiện khỏi bookmark");
    }

    @Override
    @Transactional
    public Response<Void> clearBookmark(String mainSku, String guestId) {
        BookmarkContext ctx = resolveContext(guestId, mainSku);
        redisService.unlink(ctx.getSavedKey());
        redisService.unlink(ctx.getStagingKey());

        log.info("Owner [{}] cleared entire bookmark of [{}]", ctx.getOwnerId(), ctx.getMainSku());
        return Response.ok(null, "Đã xóa toàn bộ bookmark cho sản phẩm chính");
    }

    @Override
    @Transactional(readOnly = true)
    public Response<List<BookmarkDetailResponse>> getAllBookmarks(String guestId) {
        String ownerId;
        Optional<User> userOpt = findAuthenticatedUser();
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            ownerId = user.getId() != null ? String.valueOf(user.getId()) : user.getUsername();
        } else if (guestId != null && !guestId.isBlank()) {
            ownerId = "guest:" + guestId.trim();
        } else {
            return Response.ok(Collections.emptyList());
        }

        Set<String> savedKeys = redisService.getTemplateForDb(0).keys(RedisTable.BOOKMARK_SAVED.getPrefix() + ownerId + ":*");
        Set<String> stagingKeys = redisService.getTemplateForDb(0).keys(RedisTable.BOOKMARK_STAGING.getPrefix() + ownerId + ":*");

        Set<String> allMainSkus = new HashSet<>();
        if (savedKeys != null) {
            for (String k : savedKeys) {
                String mainSku = k.substring((RedisTable.BOOKMARK_SAVED.getPrefix() + ownerId + ":").length());
                allMainSkus.add(mainSku);
            }
        }
        if (stagingKeys != null) {
            for (String k : stagingKeys) {
                String mainSku = k.substring((RedisTable.BOOKMARK_STAGING.getPrefix() + ownerId + ":").length());
                allMainSkus.add(mainSku);
            }
        }

        List<BookmarkDetailResponse> result = new ArrayList<>();
        for (String mainSku : allMainSkus) {
            BookmarkContext ctx = resolveContext(guestId, mainSku);
            BookmarkDetailResponse res = getBookmarkDetailFromKey(ctx.getSavedKey(), mainSku);
            if (res == null || res.getTotalItems() == null || res.getTotalItems() == 0) {
                BookmarkDetailResponse stRes = getBookmarkDetailFromKey(ctx.getStagingKey(), mainSku);
                if (stRes != null && stRes.getTotalItems() != null && stRes.getTotalItems() > 0) {
                    result.add(stRes);
                }
            } else {
                result.add(res);
            }
        }

        return Response.ok(result);
    }

    private BookmarkDetailResponse getBookmarkDetailFromKey(String key, String mainSku) {
        Map<Object, Object> raw = redisService.hGetAll(key);
        if (raw == null || raw.isEmpty()) {
            return BookmarkDetailResponse.empty(mainSku);
        }

        List<String> skus = raw.keySet().stream().map(Object::toString).toList();
        Map<String, Attributes> attrMap = attributesRepository.findAllBySku_skuIn(skus).stream()
                .filter(a -> a.getSku() != null && a.getSku().getSku() != null)
                .collect(Collectors.toMap(a -> a.getSku().getSku(), a -> a, (a1, a2) -> a1));

        List<BookmarkItemResponse> items = new ArrayList<>();
        double totalPrice = 0.0;
        double totalSalePrice = 0.0;
        int totalCount = 0;

        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            String sku = entry.getKey().toString();
            int qty;
            try {
                qty = Integer.parseInt(entry.getValue().toString());
            } catch (Exception e) {
                continue;
            }
            if (qty <= 0) continue;

            Attributes attr = attrMap.get(sku);
            if (attr == null) {
                // Auto-clean dead/invalid SKU from Redis
                redisService.hDelete(key, sku);
                continue;
            }

            Product product = attr.getProduct();
            String productName = product != null ? product.getName() : (attr.getName() != null ? attr.getName() : sku);
            String imageUrl = (product != null && product.getMediaItems() != null && !product.getMediaItems().isEmpty())
                    ? product.getMediaItems().get(0).getUrl()
                    : "https://images.unsplash.com/photo-1546868871-7041f2a55e12?q=80&w=400&auto=format&fit=crop";

            double unitPrice = attr.getPrice();
            double salePrice = (attr.getSalePrice() > 0) ? attr.getSalePrice() : unitPrice;
            double subTotal = salePrice * qty;

            totalPrice += (unitPrice * qty);
            totalSalePrice += subTotal;
            totalCount += qty;

            boolean isAvailable = (attr.getStatusProduct() == StockStatus.AVAILABLE);

            items.add(BookmarkItemResponse.builder()
                    .sku(sku)
                    .productName(productName)
                    .imageUrl(imageUrl)
                    .attributesTitle(attr.getName() != null ? attr.getName() : "Phụ kiện đi kèm")
                    .unitPrice(unitPrice)
                    .salePrice(salePrice)
                    .quantity(qty)
                    .subTotal(subTotal)
                    .isAvailable(isAvailable)
                    .stock(isAvailable ? 999 : 0)
                    .build());
        }

        Long ttlRemaining = redisService.getExpireSeconds(key);
        long safeTtl = (ttlRemaining != null && ttlRemaining > 0) ? ttlRemaining : 0L;
        Long expiresAtEpochMs = safeTtl > 0 ? (System.currentTimeMillis() + (safeTtl * 1000L)) : null;
        String formattedRemainingTime = formatRemainingTime(safeTtl);
        double totalDiscount = Math.max(0.0, totalPrice - totalSalePrice);

        return BookmarkDetailResponse.builder()
                .mainSku(mainSku)
                .totalItems(totalCount)
                .totalPrice(totalPrice)
                .totalSalePrice(totalSalePrice)
                .totalDiscount(totalDiscount)
                .ttlSecondsRemaining(safeTtl)
                .expiresAtEpochMs(expiresAtEpochMs)
                .formattedRemainingTime(formattedRemainingTime)
                .items(items)
                .build();
    }

    private String formatRemainingTime(long seconds) {
        if (seconds <= 0) {
            return "Đã hết hạn";
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) {
            return hours > 0 ? String.format("%d ngày %d giờ", days, hours) : String.format("%d ngày", days);
        } else if (hours > 0) {
            return minutes > 0 ? String.format("%d giờ %d phút", hours, minutes) : String.format("%d giờ", hours);
        } else if (minutes > 0) {
            return secs > 0 ? String.format("%d phút %d giây", minutes, secs) : String.format("%d phút", minutes);
        } else {
            return String.format("%d giây", secs);
        }
    }
}
