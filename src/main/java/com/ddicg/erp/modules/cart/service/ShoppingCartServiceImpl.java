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
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Quản lý giỏ hàng với tầng lưu trữ chính trên Redis Hash (in-memory) tại DB 0.
 * Bảng logic: cart:items:{userId}
 * TTL trượt: 30 ngày tự động gia hạn khi có thao tác.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShoppingCartServiceImpl implements ShoppingCartService {

    public static final long CART_TTL_DAYS = 30;

    RedisService redisService;
    AttributesRepository attributesRepository;
    UserRepository userRepository;
    SecurityUtil securityUtil;

    @Override
    public Response<ShoppingCartDto> getCart() {
        User user = getCurrentUser();
        String cartKey = getCartKey(user);
        ShoppingCartDto dto = fetchAndEnrichCart(user, cartKey);
        redisService.expire(cartKey, CART_TTL_DAYS, TimeUnit.DAYS);
        return Response.ok(dto);
    }

    @Override
    public Response<Integer> getCartCount() {
        User user = getCurrentUser();
        String cartKey = getCartKey(user);
        List<Object> quantities = redisService.hValues(cartKey);
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
    public Response<ShoppingCartDto> addToCart(final List<CartItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Danh sách sản phẩm không được rỗng");
        }

        User user = getCurrentUser();
        String cartKey = getCartKey(user);

        List<String> skus = items.stream().map(CartItemRequest::getSku).distinct().toList();
        Map<String, Attributes> attributesMap = attributesRepository.findAllBySku_skuIn(skus).stream()
                .filter(a -> a.getSku() != null && a.getSku().getSku() != null)
                .collect(Collectors.toMap(a -> a.getSku().getSku(), a -> a, (a1, a2) -> a1));

        for (CartItemRequest itemReq : items) {
            String sku = itemReq.getSku();
            int requestedQuantity = itemReq.getQuantity();

            Attributes attributes = attributesMap.get(sku);
            if (attributes == null) {
                throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Sản phẩm [" + sku + "] không tồn tại");
            }

            // Tăng số lượng trên Redis Hash
            redisService.hIncrBy(cartKey, sku, requestedQuantity);
        }

        redisService.expire(cartKey, CART_TTL_DAYS, TimeUnit.DAYS);
        ShoppingCartDto dto = fetchAndEnrichCart(user, cartKey);
        log.info("User [{}] đã thêm {} sản phẩm vào giỏ hàng Redis", user.getUsername(), items.size());

        return Response.ok(dto, "Thêm sản phẩm vào giỏ hàng thành công");
    }

    @Override
    public Response<ShoppingCartDto> updateItemQuantity(final String sku, final Integer quantity) {
        if (sku == null || sku.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "SKU không được để trống");
        }
        if (quantity == null || quantity < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Số lượng không hợp lệ");
        }

        User user = getCurrentUser();
        String cartKey = getCartKey(user);

        if (quantity == 0) {
            redisService.hDelete(cartKey, sku);
        } else {
            Object currentQty = redisService.hGet(cartKey, sku);
            if (currentQty == null) {
                throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Sản phẩm không có trong giỏ hàng");
            }
            redisService.hSet(cartKey, sku, quantity.toString());
        }

        redisService.expire(cartKey, CART_TTL_DAYS, TimeUnit.DAYS);
        ShoppingCartDto dto = fetchAndEnrichCart(user, cartKey);
        log.info("User [{}] đã cập nhật SKU [{}] với số lượng {} trên Redis", user.getUsername(), sku, quantity);

        return Response.ok(dto, "Cập nhật số lượng sản phẩm thành công");
    }

    @Override
    public Response<ShoppingCartDto> removeItem(final String sku) {
        if (sku == null || sku.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "SKU không được để trống");
        }

        User user = getCurrentUser();
        String cartKey = getCartKey(user);

        Object existing = redisService.hGet(cartKey, sku);
        if (existing == null) {
            throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Không tìm thấy sản phẩm trong giỏ hàng");
        }

        redisService.hDelete(cartKey, sku);
        redisService.expire(cartKey, CART_TTL_DAYS, TimeUnit.DAYS);
        ShoppingCartDto dto = fetchAndEnrichCart(user, cartKey);
        log.info("User [{}] đã xóa SKU [{}] khỏi giỏ hàng Redis", user.getUsername(), sku);

        return Response.ok(dto, "Đã xóa sản phẩm khỏi giỏ hàng");
    }

    @Override
    public Response<ShoppingCartDto> removeItems(final List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Danh sách SKU cần xóa không được rỗng");
        }

        User user = getCurrentUser();
        String cartKey = getCartKey(user);

        redisService.hDelete(cartKey, skus.toArray(new Object[0]));
        redisService.expire(cartKey, CART_TTL_DAYS, TimeUnit.DAYS);
        ShoppingCartDto dto = fetchAndEnrichCart(user, cartKey);
        log.info("User [{}] đã xóa {} sản phẩm khỏi giỏ hàng Redis", user.getUsername(), skus.size());

        return Response.ok(dto, "Đã xóa các sản phẩm được chọn khỏi giỏ hàng");
    }

    @Override
    public Response<ShoppingCartDto> clearCart() {
        User user = getCurrentUser();
        String cartKey = getCartKey(user);

        redisService.unlink(cartKey);
        log.info("User [{}] đã xóa toàn bộ giỏ hàng Redis", user.getUsername());

        return Response.ok(emptyCartDto(user), "Đã xóa toàn bộ giỏ hàng");
    }

    /**
     * Lấy dữ liệu từ Redis Hash, enrich thông tin chi tiết từ DB/Cache và tự động dọn dẹp (auto-clean) SKU rác
     */
    private ShoppingCartDto fetchAndEnrichCart(User user, String cartKey) {
        Map<Object, Object> rawEntries = redisService.hGetAll(cartKey);
        if (rawEntries == null || rawEntries.isEmpty()) {
            return emptyCartDto(user);
        }

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

        for (Map.Entry<Object, Object> entry : rawEntries.entrySet()) {
            String sku = entry.getKey().toString();
            int qty;
            try {
                qty = Integer.parseInt(entry.getValue().toString());
            } catch (NumberFormatException e) {
                continue;
            }

            Attributes attr = attributesMap.get(sku);
            // Auto-clean: Tự động xóa khỏi Redis nếu SKU đã bị xóa khỏi hệ thống
            if (attr == null) {
                log.warn("Auto-clean: Removing dead SKU [{}] from Redis cart of user [{}]", sku, user.getUsername());
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
                    .build());
        }

        double totalDiscount = Math.max(0.0, totalPrice - totalSalePrice);

        return ShoppingCartDto.builder()
                .username(user.getUsername())
                .items(itemResponses)
                .totalItems(totalItems)
                .totalPrice(totalPrice)
                .totalSalePrice(totalSalePrice)
                .totalDiscount(totalDiscount)
                .finalAmount(totalSalePrice)
                .build();
    }

    private ShoppingCartDto emptyCartDto(User user) {
        return ShoppingCartDto.builder()
                .username(user != null ? user.getUsername() : null)
                .items(List.of())
                .totalItems(0)
                .totalPrice(0.0)
                .totalSalePrice(0.0)
                .totalDiscount(0.0)
                .finalAmount(0.0)
                .build();
    }

    private String getCartKey(User user) {
        return RedisTable.CART_ITEMS.key(user.getId() != null ? user.getId() : user.getUsername());
    }

    private User getCurrentUser() {
        String username = securityUtil.getCurrentUsername();
        return userRepository.findByNameOrEmail(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User không tồn tại"));
    }
}
