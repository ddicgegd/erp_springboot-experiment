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
import com.ddicg.erp.modules.cart.model.CartContext;
import com.ddicg.erp.modules.cart.storage.CartStorageFactory;
import com.ddicg.erp.modules.cart.storage.CartStorageStrategy;
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
import java.util.stream.Collectors;

/**
 * Quản lý giỏ hàng đa tầng (Multi-Tiered Cart Storage):
 *   - Khách vãng lai (Guest): Lưu ngắn hạn trên Redis (TTL 7 ngày)
 *   - Thành viên thường (MEMBER, BRONZE): Lưu trên Redis (TTL 30 ngày)
 *   - Thành viên VIP (SILVER, GOLD, PLATINUM, DIAMOND): Lưu vĩnh viễn trong Database + Redis Cache
 */
@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShoppingCartServiceImpl implements ShoppingCartService {

    public static final long CART_TTL_DAYS = 30;
    public static final long GUEST_CART_TTL_DAYS = 7;
    public static final int MAX_QUANTITY_PER_ITEM = 99;
    public static final int MAX_DISTINCT_ITEMS_PER_CART = 50;

    private static final Set<String> FAST_PATH_FIELDS = Set.of(
            "username",
            "totalitems",
            "items.sku",
            "items.quantity"
    );

    CartStorageFactory cartStorageFactory;
    RedisService redisService;
    AttributesRepository attributesRepository;
    UserRepository userRepository;
    SecurityUtil securityUtil;

    private Map<String, Attributes> fetchAttributesMap(Collection<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> skuList = (skus instanceof List<String> list) ? list : new ArrayList<>(skus);
        return attributesRepository.findAllBySku_skuIn(skuList).stream()
                .filter(a -> a.getSku() != null && a.getSku().getSku() != null)
                .collect(Collectors.toMap(a -> a.getSku().getSku(), a -> a, (a1, a2) -> a1));
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
                    .user(user)
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
                    .user(null)
                    .build();
        }

        throw new BusinessException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập hoặc cung cấp header X-Guest-Id");
    }

    @Override
    public Response<ShoppingCartDto> getCart(final String guestId, final List<String> fields, final List<String> include) {
        CartContext ctx = resolveContext(guestId);
        CartStorageStrategy strategy = cartStorageFactory.getStrategy(ctx);
        ShoppingCartDto dto = fetchAndProjectCart(ctx, strategy, fields, include);
        return Response.ok(dto);
    }

    @Override
    public Response<Integer> getCartCount(final String guestId) {
        CartContext ctx = resolveContext(guestId);
        CartStorageStrategy strategy = cartStorageFactory.getStrategy(ctx);
        int totalCount = strategy.getTotalItemsCount(ctx);
        return Response.ok(totalCount);
    }

    @Override
    @Transactional
    public Response<ShoppingCartDto> addToCart(final List<CartItemRequest> items, final String guestId) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Danh sách sản phẩm không được rỗng");
        }

        CartContext ctx = resolveContext(guestId);
        CartStorageStrategy strategy = cartStorageFactory.getStrategy(ctx);

        // 1. Kiểm tra giới hạn số loại sản phẩm khác nhau trong giỏ (max 50)
        Map<String, Integer> currentEntries = strategy.getCartEntries(ctx);
        Set<String> existingSkus = new HashSet<>(currentEntries.keySet());

        Set<String> incomingSkus = items.stream().map(CartItemRequest::getSku).collect(Collectors.toSet());
        Set<String> combinedSkus = new HashSet<>(existingSkus);
        combinedSkus.addAll(incomingSkus);

        if (combinedSkus.size() > MAX_DISTINCT_ITEMS_PER_CART) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Giỏ hàng chỉ chứa tối đa " + MAX_DISTINCT_ITEMS_PER_CART + " loại sản phẩm khác nhau");
        }

        // 2. Fetch và validate Attributes từ DB
        Map<String, Attributes> attributesMap = fetchAttributesMap(incomingSkus);

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
            int currentQty = currentEntries.getOrDefault(sku, 0);
            if (currentQty + requestedQuantity > MAX_QUANTITY_PER_ITEM) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Tổng số lượng sản phẩm [" + sku + "] trong giỏ hàng không được vượt quá " + MAX_QUANTITY_PER_ITEM);
            }

            strategy.addOrIncrementItem(ctx, sku, requestedQuantity);
        }

        ShoppingCartDto dto = fetchAndProjectCart(ctx, strategy, null, null);
        log.info("Owner [{}] (VIP={}) đã thêm {} sản phẩm vào giỏ hàng", ctx.getOwnerName(), ctx.isVip(), items.size());

        return Response.ok(dto, "Thêm sản phẩm vào giỏ hàng thành công");
    }

    @Override
    @Transactional
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
        CartStorageStrategy strategy = cartStorageFactory.getStrategy(ctx);

        if (quantity == 0) {
            strategy.setItemQuantity(ctx, sku, 0);
        } else {
            Map<String, Integer> entries = strategy.getCartEntries(ctx);
            if (!entries.containsKey(sku)) {
                throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Sản phẩm không có trong giỏ hàng");
            }

            // Validate SKU availability
            List<Attributes> attrs = attributesRepository.findAllBySku_skuIn(List.of(sku));
            if (attrs.isEmpty()) {
                strategy.removeItem(ctx, sku);
                throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Sản phẩm [" + sku + "] không tồn tại");
            }
            Attributes attr = attrs.get(0);
            if (attr.getStatusProduct() != StockStatus.AVAILABLE) {
                throw new BusinessException(ErrorCode.ATTRIBUTES_OUT_OF_STOCK,
                        "Sản phẩm [" + sku + "] hiện không khả dụng (" + attr.getStatusProduct().getValue() + ")");
            }

            strategy.setItemQuantity(ctx, sku, quantity);
        }

        ShoppingCartDto dto = fetchAndProjectCart(ctx, strategy, null, null);
        log.info("Owner [{}] đã cập nhật SKU [{}] với số lượng {}", ctx.getOwnerName(), sku, quantity);

        return Response.ok(dto, "Cập nhật số lượng sản phẩm thành công");
    }

    @Override
    @Transactional
    public Response<ShoppingCartDto> removeItem(final String sku, final String guestId) {
        if (sku == null || sku.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "SKU không được để trống");
        }

        CartContext ctx = resolveContext(guestId);
        CartStorageStrategy strategy = cartStorageFactory.getStrategy(ctx);

        Map<String, Integer> entries = strategy.getCartEntries(ctx);
        if (!entries.containsKey(sku)) {
            throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Không tìm thấy sản phẩm trong giỏ hàng");
        }

        strategy.removeItem(ctx, sku);
        ShoppingCartDto dto = fetchAndProjectCart(ctx, strategy, null, null);
        log.info("Owner [{}] đã xóa SKU [{}] khỏi giỏ hàng", ctx.getOwnerName(), sku);

        return Response.ok(dto, "Đã xóa sản phẩm khỏi giỏ hàng");
    }

    @Override
    @Transactional
    public Response<ShoppingCartDto> removeItems(final List<String> skus, final String guestId) {
        if (skus == null || skus.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Danh sách SKU cần xóa không được rỗng");
        }

        CartContext ctx = resolveContext(guestId);
        CartStorageStrategy strategy = cartStorageFactory.getStrategy(ctx);

        strategy.removeItems(ctx, skus);
        ShoppingCartDto dto = fetchAndProjectCart(ctx, strategy, null, null);
        log.info("Owner [{}] đã xóa {} sản phẩm khỏi giỏ hàng", ctx.getOwnerName(), skus.size());

        return Response.ok(dto, "Đã xóa các sản phẩm được chọn khỏi giỏ hàng");
    }

    @Override
    @Transactional
    public Response<ShoppingCartDto> clearCart(final String guestId) {
        CartContext ctx = resolveContext(guestId);
        CartStorageStrategy strategy = cartStorageFactory.getStrategy(ctx);

        strategy.clearCart(ctx);
        log.info("Owner [{}] đã xóa toàn bộ giỏ hàng", ctx.getOwnerName());

        return Response.ok(emptyCartDto(ctx.getOwnerName()), "Đã xóa toàn bộ giỏ hàng");
    }

    @Override
    @Transactional
    public Response<ShoppingCartDto> mergeCart(final String guestId) {
        User user = findAuthenticatedUser()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để hợp nhất giỏ hàng"));

        CartContext userCtx = resolveContext(null);
        CartStorageStrategy userStrategy = cartStorageFactory.getStrategy(userCtx);

        if (guestId == null || guestId.isBlank()) {
            return Response.ok(fetchAndProjectCart(userCtx, userStrategy, null, null), "Hợp nhất giỏ hàng thành công");
        }

        String guestCartKey = RedisTable.CART_GUEST_ITEMS.key(guestId.trim());
        Map<Object, Object> guestRaw = redisService.hGetAll(guestCartKey);

        if (guestRaw == null || guestRaw.isEmpty()) {
            return Response.ok(fetchAndProjectCart(userCtx, userStrategy, null, null), "Hợp nhất giỏ hàng thành công");
        }

        List<String> guestSkus = guestRaw.keySet().stream().map(Object::toString).toList();
        Map<String, Attributes> attributesMap = fetchAttributesMap(guestSkus);
        Map<String, Integer> userEntries = userStrategy.getCartEntries(userCtx);

        for (Map.Entry<Object, Object> entry : guestRaw.entrySet()) {
            String sku = entry.getKey().toString();
            int guestQty = parseQuantity(entry.getValue());
            if (guestQty <= 0) {
                continue;
            }

            Attributes attr = attributesMap.get(sku);
            // Chỉ merge những SKU còn tồn tại và AVAILABLE
            if (attr == null || attr.getStatusProduct() != StockStatus.AVAILABLE) {
                continue;
            }

            int userQty = userEntries.getOrDefault(sku, 0);
            int mergedQty = Math.min(MAX_QUANTITY_PER_ITEM, userQty + guestQty);
            userStrategy.setItemQuantity(userCtx, sku, mergedQty);
        }

        // Xóa giỏ hàng guest sau khi merge
        redisService.unlink(guestCartKey);

        ShoppingCartDto dto = fetchAndProjectCart(userCtx, userStrategy, null, null);
        log.info("User [{}] đã hợp nhất giỏ hàng từ Guest [{}] thành công", user.getUsername(), guestId);

        return Response.ok(dto, "Hợp nhất giỏ hàng thành công");
    }

    private static int parseQuantity(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Tầng phân giải dữ liệu (GraphQL-style DataLoader & Projection pipeline).
     */
    private ShoppingCartDto fetchAndProjectCart(
            CartContext ctx,
            CartStorageStrategy strategy,
            List<String> rawFields,
            List<String> rawInclude) {

        Map<String, Integer> entries = strategy.getCartEntries(ctx);
        if (entries == null || entries.isEmpty()) {
            return emptyCartDto(ctx.getOwnerName());
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

        // Fast path: Pure In-Memory resolution (no DB query!)
        if (!requiresDbEnrichment) {
            return buildFastRedisCart(ctx.getOwnerName(), entries, requestedFields);
        }

        // Deep path: DataLoader with Batch DB query
        return buildEnrichedProjectedCart(ctx, strategy, entries, requestedFields, requestedInclude);
    }

    private boolean requiresDatabase(Set<String> fields, Set<String> include) {
        if (!include.isEmpty()) {
            return true;
        }
        return fields.stream()
                .map(String::toLowerCase)
                .anyMatch(f -> !FAST_PATH_FIELDS.contains(f));
    }

    private ShoppingCartDto buildFastRedisCart(
            String ownerName,
            Map<String, Integer> entries,
            Set<String> fields) {

        int totalCount = 0;
        List<CartItemResponse> items = new ArrayList<>();
        boolean wantsItems = fields.contains("items") || fields.stream().anyMatch(f -> f.startsWith("items."));

        for (Map.Entry<String, Integer> entry : entries.entrySet()) {
            String sku = entry.getKey();
            int qty = entry.getValue();
            if (qty <= 0) continue;
            totalCount += qty;

            if (wantsItems) {
                CartItemResponse.CartItemResponseBuilder itemB = CartItemResponse.builder();
                if (isItemFieldRequested(fields, "sku")) {
                    itemB.sku(sku);
                }
                if (isItemFieldRequested(fields, "quantity")) {
                    itemB.quantity(qty);
                }
                items.add(itemB.build());
            }
        }

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
            CartContext ctx,
            CartStorageStrategy strategy,
            Map<String, Integer> entries,
            Set<String> fields,
            Set<String> include) {

        List<String> skus = new ArrayList<>(entries.keySet());
        Map<String, Attributes> attributesMap = fetchAttributesMap(skus);

        List<CartItemResponse> itemResponses = new ArrayList<>();
        double totalPrice = 0.0;
        double totalSalePrice = 0.0;
        int totalItems = 0;

        boolean isSparse = !fields.isEmpty();
        boolean wantsItems = !isSparse || fields.contains("items") || fields.stream().anyMatch(f -> f.startsWith("items."));
        boolean includeSpecs = include.contains("specifications") || fields.contains("items.specifications") || fields.contains("specifications");
        boolean includePromos = include.contains("promotions") || fields.contains("items.promotions") || fields.contains("promotions");

        for (Map.Entry<String, Integer> entry : entries.entrySet()) {
            String sku = entry.getKey();
            int qty = entry.getValue();
            if (qty <= 0) continue;

            Attributes attr = attributesMap.get(sku);
            if (attr == null) {
                log.warn("Auto-clean: Removing dead SKU [{}] from cart of [{}]", sku, ctx.getOwnerName());
                strategy.removeItem(ctx, sku);
                continue;
            }

            Product product = attr.getProduct();
            String productName = product != null ? product.getName() : attr.getName();
            String imageUrl = (product != null && product.getMediaItems() != null && !product.getMediaItems().isEmpty())
                    ? product.getMediaItems().get(0).getUrl()
                    : null;

            double unitPrice = attr.getPrice();
            double salePrice = (attr.getSalePrice() > 0) ? attr.getSalePrice() : unitPrice;
            double subTotal = salePrice * qty;

            totalPrice += (unitPrice * qty);
            totalSalePrice += subTotal;
            totalItems += qty;

            boolean isAvailable = (attr.getStatusProduct() == StockStatus.AVAILABLE);
            Integer stock = isAvailable ? 999 : 0;

            if (wantsItems) {
                CartItemResponse.CartItemResponseBuilder itemB = CartItemResponse.builder();
                if (!isSparse || isItemFieldRequested(fields, "sku")) itemB.sku(sku);
                if (!isSparse || isItemFieldRequested(fields, "productName")) itemB.productName(productName);
                if (!isSparse || isItemFieldRequested(fields, "imageUrl")) itemB.imageUrl(imageUrl);
                if (!isSparse || isItemFieldRequested(fields, "attributesTitle")) itemB.attributesTitle(attr.getName());
                if (!isSparse || isItemFieldRequested(fields, "unitPrice")) itemB.unitPrice(unitPrice);
                if (!isSparse || isItemFieldRequested(fields, "salePrice")) itemB.salePrice(salePrice);
                if (!isSparse || isItemFieldRequested(fields, "quantity")) itemB.quantity(qty);
                if (!isSparse || isItemFieldRequested(fields, "subTotal")) itemB.subTotal(subTotal);
                if (!isSparse || isItemFieldRequested(fields, "isAvailable")) itemB.isAvailable(isAvailable);
                if (!isSparse || isItemFieldRequested(fields, "stock")) itemB.stock(stock);
                if (includeSpecs) itemB.specifications(attr.getSpecifications());
                if (includePromos) itemB.promotions(attr.getPromotions());
                itemResponses.add(itemB.build());
            }
        }

        double totalDiscount = Math.max(0.0, totalPrice - totalSalePrice);

        ShoppingCartDto.ShoppingCartDtoBuilder dtoB = ShoppingCartDto.builder();
        if (!isSparse || fields.contains("username")) dtoB.username(ctx.getOwnerName());
        if (wantsItems) dtoB.items(itemResponses);
        if (!isSparse || fields.contains("totalItems")) dtoB.totalItems(totalItems);
        if (!isSparse || fields.contains("totalPrice")) dtoB.totalPrice(totalPrice);
        if (!isSparse || fields.contains("totalSalePrice")) dtoB.totalSalePrice(totalSalePrice);
        if (!isSparse || fields.contains("totalDiscount")) dtoB.totalDiscount(totalDiscount);
        if (!isSparse || fields.contains("finalAmount")) dtoB.finalAmount(totalSalePrice);

        return dtoB.build();
    }

    private boolean isItemFieldRequested(Set<String> fields, String fieldName) {
        return fields.contains("items") || fields.contains(fieldName) || fields.contains("items." + fieldName);
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
