package com.ddicg.erp.modules.cart.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.modules.cart.dto.CartItemResponse;
import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.merchandise.model.Product;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Quản lý giỏ hàng với tầng lưu trữ chính trên Redis Hash (in-memory) tại DB 0.
 * Bảng logic:
 *   - User: cart:items:{userId} (TTL 30 ngày)
 *   - Guest: cart:guest:items:{guestId} (TTL 7 ngày)
 */
@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShoppingCartServiceImpl implements ShoppingCartService {

    public static final long CART_TTL_DAYS = 30;
    public static final long GUEST_CART_TTL_DAYS = 7;
    public static final int MAX_QUANTITY_PER_ITEM = 99;
    public static final int MAX_DISTINCT_ITEMS_PER_CART = 50;

    RedisService redisService;
    AttributesRepository attributesRepository;
    UserRepository userRepository;
    SecurityUtil securityUtil;

    @Getter
    @Builder
    private static class CartContext {
        String key;
        String ownerName;
        long ttlDays;
        boolean isGuest;
    }

    private Optional<User> findAuthenticatedUser() {
        String username = securityUtil.getCurrentUsername();
        if (username == null || "anonymous".equalsIgnoreCase(username) || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByNameOrEmail(username);
    }

    private CartContext resolveContext(String guestId) {
        Optional<User> userOpt = findAuthenticatedUser();
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String key = RedisTable.CART_ITEMS.key(user.getId() != null ? user.getId() : user.getUsername());
            return CartContext.builder()
                    .key(key)
                    .ownerName(user.getUsername())
                    .ttlDays(CART_TTL_DAYS)
                    .isGuest(false)
                    .build();
        }

        if (guestId != null && !guestId.isBlank()) {
            String trimmedGuestId = guestId.trim();
            String key = RedisTable.CART_GUEST_ITEMS.key(trimmedGuestId);
            return CartContext.builder()
                    .key(key)
                    .ownerName("guest:" + trimmedGuestId)
                    .ttlDays(GUEST_CART_TTL_DAYS)
                    .isGuest(true)
                    .build();
        }

        throw new BusinessException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập hoặc cung cấp header X-Guest-Id");
    }

    @Override
    public Response<ShoppingCartDto> getCart(final String guestId, final List<String> fields, final List<String> include) {
        CartContext ctx = resolveContext(guestId);
        ShoppingCartDto dto = fetchAndProjectCart(ctx.getOwnerName(), ctx.getKey(), ctx.getTtlDays(), fields, include);
        return Response.ok(dto);
    }

    @Override
    public Response<Integer> getCartCount(final String guestId) {
        CartContext ctx = resolveContext(guestId);
        List<Object> quantities = redisService.hValues(ctx.getKey());
        if (quantities == null || quantities.isEmpty()) {
            return Response.ok(0);
        }
        int totalCount = quantities.stream()
                .mapToInt(q -> {
                    try {
                        return Integer.parseInt(q.toString());
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .sum();
        return Response.ok(totalCount);
    }

    @Override
    public Response<ShoppingCartDto> addToCart(final List<CartItemRequest> items, final String guestId) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Danh sách sản phẩm không được rỗng");
        }

        CartContext ctx = resolveContext(guestId);
        String cartKey = ctx.getKey();

        // 1. Kiểm tra giới hạn số loại sản phẩm khác nhau trong giỏ (max 50)
        Map<Object, Object> currentEntries = redisService.hGetAll(cartKey);
        Set<String> existingSkus = (currentEntries != null)
                ? currentEntries.keySet().stream().map(Object::toString).collect(Collectors.toSet())
                : new HashSet<>();

        Set<String> incomingSkus = items.stream().map(CartItemRequest::getSku).collect(Collectors.toSet());
        Set<String> combinedSkus = new HashSet<>(existingSkus);
        combinedSkus.addAll(incomingSkus);

        if (combinedSkus.size() > MAX_DISTINCT_ITEMS_PER_CART) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Giỏ hàng chỉ chứa tối đa " + MAX_DISTINCT_ITEMS_PER_CART + " loại sản phẩm khác nhau");
        }

        // 2. Fetch và validate Attributes từ DB
        List<String> skuList = incomingSkus.stream().toList();
        Map<String, Attributes> attributesMap = attributesRepository.findAllBySku_skuIn(skuList).stream()
                .filter(a -> a.getSku() != null && a.getSku().getSku() != null)
                .collect(Collectors.toMap(a -> a.getSku().getSku(), a -> a, (a1, a2) -> a1));

        for (CartItemRequest itemReq : items) {
            String sku = itemReq.getSku();
            Integer requestedQuantity = itemReq.getQuantity();

            if (requestedQuantity == null || requestedQuantity <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Số lượng sản phẩm phải lớn hơn 0");
            }
            if (requestedQuantity > MAX_QUANTITY_PER_ITEM) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Số lượng cho mỗi sản phẩm không được vượt quá " + MAX_QUANTITY_PER_ITEM);
            }

            Attributes attributes = attributesMap.get(sku);
            if (attributes == null) {
                throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Sản phẩm [" + sku + "] không tồn tại");
            }

            // Kiểm tra trạng thái tồn kho
            if (attributes.getStatusProduct() != StockStatus.AVAILABLE) {
                throw new BusinessException(ErrorCode.ATTRIBUTES_OUT_OF_STOCK,
                        "Sản phẩm [" + sku + "] hiện không khả dụng (" + attributes.getStatusProduct().getValue() + ")");
            }

            // Kiểm tra tổng số lượng sau khi cộng dồn không vượt quá MAX_QUANTITY_PER_ITEM
            Object currentQtyObj = (currentEntries != null) ? currentEntries.get(sku) : null;
            int currentQty = 0;
            if (currentQtyObj != null) {
                try {
                    currentQty = Integer.parseInt(currentQtyObj.toString());
                } catch (NumberFormatException ignored) {}
            }

            if (currentQty + requestedQuantity > MAX_QUANTITY_PER_ITEM) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Tổng số lượng sản phẩm [" + sku + "] trong giỏ hàng không được vượt quá " + MAX_QUANTITY_PER_ITEM);
            }

            // Tăng số lượng trên Redis Hash
            redisService.hIncrBy(cartKey, sku, requestedQuantity);
        }

        redisService.expire(cartKey, ctx.getTtlDays(), TimeUnit.DAYS);
        ShoppingCartDto dto = fetchAndProjectCart(ctx.getOwnerName(), cartKey, ctx.getTtlDays(), null, null);
        log.info("Owner [{}] đã thêm {} sản phẩm vào giỏ hàng Redis", ctx.getOwnerName(), items.size());

        return Response.ok(dto, "Thêm sản phẩm vào giỏ hàng thành công");
    }

    @Override
    public Response<ShoppingCartDto> updateItemQuantity(final String sku, final Integer quantity, final String guestId) {
        if (sku == null || sku.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "SKU không được để trống");
        }
        if (quantity == null || quantity < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Số lượng không hợp lệ");
        }
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Số lượng cho mỗi sản phẩm không được vượt quá " + MAX_QUANTITY_PER_ITEM);
        }

        CartContext ctx = resolveContext(guestId);
        String cartKey = ctx.getKey();

        if (quantity == 0) {
            redisService.hDelete(cartKey, sku);
        } else {
            Object currentQty = redisService.hGet(cartKey, sku);
            if (currentQty == null) {
                throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Sản phẩm không có trong giỏ hàng");
            }

            // Validate SKU availability
            List<Attributes> attrs = attributesRepository.findAllBySku_skuIn(List.of(sku));
            if (attrs.isEmpty()) {
                redisService.hDelete(cartKey, sku);
                throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Sản phẩm [" + sku + "] không tồn tại");
            }
            Attributes attr = attrs.get(0);
            if (attr.getStatusProduct() != StockStatus.AVAILABLE) {
                throw new BusinessException(ErrorCode.ATTRIBUTES_OUT_OF_STOCK,
                        "Sản phẩm [" + sku + "] hiện không khả dụng (" + attr.getStatusProduct().getValue() + ")");
            }

            redisService.hSet(cartKey, sku, quantity.toString());
        }

        redisService.expire(cartKey, ctx.getTtlDays(), TimeUnit.DAYS);
        ShoppingCartDto dto = fetchAndProjectCart(ctx.getOwnerName(), cartKey, ctx.getTtlDays(), null, null);
        log.info("Owner [{}] đã cập nhật SKU [{}] với số lượng {} trên Redis", ctx.getOwnerName(), sku, quantity);

        return Response.ok(dto, "Cập nhật số lượng sản phẩm thành công");
    }

    @Override
    public Response<ShoppingCartDto> removeItem(final String sku, final String guestId) {
        if (sku == null || sku.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "SKU không được để trống");
        }

        CartContext ctx = resolveContext(guestId);
        String cartKey = ctx.getKey();

        Object existing = redisService.hGet(cartKey, sku);
        if (existing == null) {
            throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Không tìm thấy sản phẩm trong giỏ hàng");
        }

        redisService.hDelete(cartKey, sku);
        redisService.expire(cartKey, ctx.getTtlDays(), TimeUnit.DAYS);
        ShoppingCartDto dto = fetchAndProjectCart(ctx.getOwnerName(), cartKey, ctx.getTtlDays(), null, null);
        log.info("Owner [{}] đã xóa SKU [{}] khỏi giỏ hàng Redis", ctx.getOwnerName(), sku);

        return Response.ok(dto, "Đã xóa sản phẩm khỏi giỏ hàng");
    }

    @Override
    public Response<ShoppingCartDto> removeItems(final List<String> skus, final String guestId) {
        if (skus == null || skus.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Danh sách SKU cần xóa không được rỗng");
        }

        CartContext ctx = resolveContext(guestId);
        String cartKey = ctx.getKey();

        redisService.hDelete(cartKey, skus.toArray(new Object[0]));
        redisService.expire(cartKey, ctx.getTtlDays(), TimeUnit.DAYS);
        ShoppingCartDto dto = fetchAndProjectCart(ctx.getOwnerName(), cartKey, ctx.getTtlDays(), null, null);
        log.info("Owner [{}] đã xóa {} sản phẩm khỏi giỏ hàng Redis", ctx.getOwnerName(), skus.size());

        return Response.ok(dto, "Đã xóa các sản phẩm được chọn khỏi giỏ hàng");
    }

    @Override
    public Response<ShoppingCartDto> clearCart(final String guestId) {
        CartContext ctx = resolveContext(guestId);
        String cartKey = ctx.getKey();

        redisService.unlink(cartKey);
        log.info("Owner [{}] đã xóa toàn bộ giỏ hàng Redis", ctx.getOwnerName());

        return Response.ok(emptyCartDto(ctx.getOwnerName()), "Đã xóa toàn bộ giỏ hàng");
    }

    @Override
    public Response<ShoppingCartDto> mergeCart(final String guestId) {
        User user = findAuthenticatedUser()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để hợp nhất giỏ hàng"));

        String userCartKey = RedisTable.CART_ITEMS.key(user.getId() != null ? user.getId() : user.getUsername());

        if (guestId == null || guestId.isBlank()) {
            return Response.ok(fetchAndProjectCart(user.getUsername(), userCartKey, CART_TTL_DAYS, null, null), "Hợp nhất giỏ hàng thành công");
        }

        String guestCartKey = RedisTable.CART_GUEST_ITEMS.key(guestId.trim());
        Map<Object, Object> guestEntries = redisService.hGetAll(guestCartKey);

        if (guestEntries == null || guestEntries.isEmpty()) {
            return Response.ok(fetchAndProjectCart(user.getUsername(), userCartKey, CART_TTL_DAYS, null, null), "Hợp nhất giỏ hàng thành công");
        }

        List<String> guestSkus = guestEntries.keySet().stream().map(Object::toString).toList();
        Map<String, Attributes> attributesMap = attributesRepository.findAllBySku_skuIn(guestSkus).stream()
                .filter(a -> a.getSku() != null && a.getSku().getSku() != null)
                .collect(Collectors.toMap(a -> a.getSku().getSku(), a -> a, (a1, a2) -> a1));

        Map<Object, Object> userEntries = redisService.hGetAll(userCartKey);

        for (Map.Entry<Object, Object> entry : guestEntries.entrySet()) {
            String sku = entry.getKey().toString();
            int guestQty;
            try {
                guestQty = Integer.parseInt(entry.getValue().toString());
            } catch (NumberFormatException e) {
                continue;
            }

            Attributes attr = attributesMap.get(sku);
            // Chỉ merge những SKU còn tồn tại và AVAILABLE
            if (attr == null || attr.getStatusProduct() != StockStatus.AVAILABLE) {
                continue;
            }

            int userQty = 0;
            if (userEntries != null && userEntries.containsKey(sku)) {
                try {
                    userQty = Integer.parseInt(userEntries.get(sku).toString());
                } catch (NumberFormatException ignored) {}
            }

            int mergedQty = Math.min(MAX_QUANTITY_PER_ITEM, userQty + guestQty);
            redisService.hSet(userCartKey, sku, String.valueOf(mergedQty));
        }

        // Xóa giỏ hàng guest sau khi merge
        redisService.unlink(guestCartKey);
        redisService.expire(userCartKey, CART_TTL_DAYS, TimeUnit.DAYS);

        ShoppingCartDto dto = fetchAndProjectCart(user.getUsername(), userCartKey, CART_TTL_DAYS, null, null);
        log.info("User [{}] đã hợp nhất giỏ hàng từ Guest [{}] thành công", user.getUsername(), guestId);

        return Response.ok(dto, "Hợp nhất giỏ hàng thành công");
    }

    /**
     * Tầng phân giải dữ liệu (GraphQL-style DataLoader & Projection pipeline).
     */
    private ShoppingCartDto fetchAndProjectCart(
            String ownerName,
            String cartKey,
            long ttlDays,
            List<String> rawFields,
            List<String> rawInclude) {

        Map<Object, Object> rawEntries = redisService.hGetAll(cartKey);
        if (rawEntries == null || rawEntries.isEmpty()) {
            return emptyCartDto(ownerName);
        }

        // Parse normalized fields & include
        Set<String> requestedFields = (rawFields != null && !rawFields.isEmpty())
                ? rawFields.stream()
                        .flatMap(f -> Arrays.stream(f.split(",")))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet())
                : Collections.emptySet();

        Set<String> requestedInclude = (rawInclude != null && !rawInclude.isEmpty())
                ? rawInclude.stream()
                        .flatMap(f -> Arrays.stream(f.split(",")))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet())
                : Collections.emptySet();

        boolean isSparseProjection = !requestedFields.isEmpty();
        boolean requiresDbEnrichment = !isSparseProjection || requiresDatabase(requestedFields, requestedInclude);

        // Fast path: Pure Redis In-Memory resolution (no DB query!)
        if (!requiresDbEnrichment) {
            return buildFastRedisCart(ownerName, cartKey, ttlDays, rawEntries, requestedFields);
        }

        // Deep path: DataLoader with Batch DB query
        return buildEnrichedProjectedCart(ownerName, cartKey, ttlDays, rawEntries, requestedFields, requestedInclude);
    }

    private boolean requiresDatabase(Set<String> fields, Set<String> include) {
        if (!include.isEmpty()) {
            return true;
        }
        for (String f : fields) {
            String lower = f.toLowerCase();
            if (lower.equals("totalprice") || lower.equals("totalsaleprice")
                    || lower.equals("totaldiscount") || lower.equals("finalamount")
                    || lower.equals("items")
                    || lower.contains("productname") || lower.contains("imageurl")
                    || lower.contains("attributestitle") || lower.contains("unitprice")
                    || lower.contains("saleprice") || lower.contains("subtotal")
                    || lower.contains("isavailable") || lower.contains("stock")
                    || lower.contains("specifications") || lower.contains("promotions")) {
                return true;
            }
        }
        return false;
    }

    private ShoppingCartDto buildFastRedisCart(
            String ownerName,
            String cartKey,
            long ttlDays,
            Map<Object, Object> rawEntries,
            Set<String> fields) {

        int totalCount = 0;
        List<CartItemResponse> items = new ArrayList<>();
        boolean wantsItems = fields.contains("items") || fields.stream().anyMatch(f -> f.startsWith("items."));

        for (Map.Entry<Object, Object> entry : rawEntries.entrySet()) {
            String sku = entry.getKey().toString();
            int qty;
            try {
                qty = Integer.parseInt(entry.getValue().toString());
            } catch (NumberFormatException e) {
                continue;
            }
            totalCount += qty;

            if (wantsItems) {
                CartItemResponse.CartItemResponseBuilder itemB = CartItemResponse.builder();
                if (fields.contains("items") || isItemFieldRequested(fields, "sku")) {
                    itemB.sku(sku);
                }
                if (fields.contains("items") || isItemFieldRequested(fields, "quantity")) {
                    itemB.quantity(qty);
                }
                items.add(itemB.build());
            }
        }

        redisService.expire(cartKey, ttlDays, TimeUnit.DAYS);

        ShoppingCartDto.ShoppingCartDtoBuilder builder = ShoppingCartDto.builder();
        if (fields.contains("username")) {
            builder.username(ownerName);
        }
        if (fields.contains("totalItems")) {
            builder.totalItems(totalCount);
        }
        if (wantsItems) {
            builder.items(items);
        }

        return builder.build();
    }

    private ShoppingCartDto buildEnrichedProjectedCart(
            String ownerName,
            String cartKey,
            long ttlDays,
            Map<Object, Object> rawEntries,
            Set<String> fields,
            Set<String> include) {

        List<String> skus = rawEntries.keySet().stream()
                .map(Object::toString)
                .toList();

        Map<String, Attributes> attributesMap = attributesRepository.findAllBySku_skuIn(skus).stream()
                .filter(a -> a.getSku() != null && a.getSku().getSku() != null)
                .collect(Collectors.toMap(a -> a.getSku().getSku(), a -> a, (a1, a2) -> a1));

        List<CartItemResponse> itemResponses = new ArrayList<>();
        double totalPrice = 0.0;
        double totalSalePrice = 0.0;
        int totalItems = 0;

        boolean isSparse = !fields.isEmpty();
        boolean wantsItems = !isSparse || fields.contains("items") || fields.stream().anyMatch(f -> f.startsWith("items."));
        boolean includeSpecs = include.contains("specifications") || fields.contains("items.specifications") || fields.contains("specifications");
        boolean includePromos = include.contains("promotions") || fields.contains("items.promotions") || fields.contains("promotions");

        for (Map.Entry<Object, Object> entry : rawEntries.entrySet()) {
            String sku = entry.getKey().toString();
            int qty;
            try {
                qty = Integer.parseInt(entry.getValue().toString());
            } catch (NumberFormatException e) {
                continue;
            }

            Attributes attr = attributesMap.get(sku);
            if (attr == null) {
                log.warn("Auto-clean: Removing dead SKU [{}] from Redis cart of [{}]", sku, ownerName);
                redisService.hDelete(cartKey, sku);
                continue;
            }

            Product product = attr.getProduct();
            String productName = product != null ? product.getName() : attr.getName();
            String imageUrl = null;
            if (product != null && product.getMediaItems() != null && !product.getMediaItems().isEmpty()) {
                imageUrl = product.getMediaItems().get(0).getUrl();
            }

            double unitPrice = attr.getPrice();
            double salePrice = (attr.getSalePrice() > 0) ? attr.getSalePrice() : unitPrice;
            double subTotal = salePrice * qty;

            totalPrice += (unitPrice * qty);
            totalSalePrice += subTotal;
            totalItems += qty;

            boolean isAvailable = (attr.getStatusProduct() == StockStatus.AVAILABLE);
            Integer stock = isAvailable ? 999 : 0;

            if (wantsItems) {
                if (!isSparse || fields.contains("items")) {
                    itemResponses.add(CartItemResponse.builder()
                            .sku(sku)
                            .productName(productName)
                            .imageUrl(imageUrl)
                            .attributesTitle(attr.getName())
                            .unitPrice(unitPrice)
                            .salePrice(salePrice)
                            .quantity(qty)
                            .subTotal(subTotal)
                            .isAvailable(isAvailable)
                            .stock(stock)
                            .specifications(includeSpecs ? attr.getSpecifications() : null)
                            .promotions(includePromos ? attr.getPromotions() : null)
                            .build());
                } else {
                    CartItemResponse.CartItemResponseBuilder itemB = CartItemResponse.builder();
                    if (isItemFieldRequested(fields, "sku")) itemB.sku(sku);
                    if (isItemFieldRequested(fields, "productName")) itemB.productName(productName);
                    if (isItemFieldRequested(fields, "imageUrl")) itemB.imageUrl(imageUrl);
                    if (isItemFieldRequested(fields, "attributesTitle")) itemB.attributesTitle(attr.getName());
                    if (isItemFieldRequested(fields, "unitPrice")) itemB.unitPrice(unitPrice);
                    if (isItemFieldRequested(fields, "salePrice")) itemB.salePrice(salePrice);
                    if (isItemFieldRequested(fields, "quantity")) itemB.quantity(qty);
                    if (isItemFieldRequested(fields, "subTotal")) itemB.subTotal(subTotal);
                    if (isItemFieldRequested(fields, "isAvailable")) itemB.isAvailable(isAvailable);
                    if (isItemFieldRequested(fields, "stock")) itemB.stock(stock);
                    if (includeSpecs) itemB.specifications(attr.getSpecifications());
                    if (includePromos) itemB.promotions(attr.getPromotions());
                    itemResponses.add(itemB.build());
                }
            }
        }

        redisService.expire(cartKey, ttlDays, TimeUnit.DAYS);
        double totalDiscount = Math.max(0.0, totalPrice - totalSalePrice);

        if (!isSparse) {
            return ShoppingCartDto.builder()
                    .username(ownerName)
                    .items(itemResponses)
                    .totalItems(totalItems)
                    .totalPrice(totalPrice)
                    .totalSalePrice(totalSalePrice)
                    .totalDiscount(totalDiscount)
                    .finalAmount(totalSalePrice)
                    .build();
        }

        ShoppingCartDto.ShoppingCartDtoBuilder dtoB = ShoppingCartDto.builder();
        if (fields.contains("username")) dtoB.username(ownerName);
        if (wantsItems) dtoB.items(itemResponses);
        if (fields.contains("totalItems")) dtoB.totalItems(totalItems);
        if (fields.contains("totalPrice")) dtoB.totalPrice(totalPrice);
        if (fields.contains("totalSalePrice")) dtoB.totalSalePrice(totalSalePrice);
        if (fields.contains("totalDiscount")) dtoB.totalDiscount(totalDiscount);
        if (fields.contains("finalAmount")) dtoB.finalAmount(totalSalePrice);

        return dtoB.build();
    }

    private boolean isItemFieldRequested(Set<String> fields, String fieldName) {
        return fields.contains(fieldName) || fields.contains("items." + fieldName);
    }

    private ShoppingCartDto emptyCartDto(String ownerName) {
        return ShoppingCartDto.builder()
                .username(ownerName)
                .items(List.of())
                .totalItems(0)
                .totalPrice(0.0)
                .totalSalePrice(0.0)
                .totalDiscount(0.0)
                .finalAmount(0.0)
                .build();
    }
}
