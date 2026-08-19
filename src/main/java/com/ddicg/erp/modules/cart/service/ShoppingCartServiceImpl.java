package com.ddicg.erp.modules.cart.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.modules.cart.dto.CartItemResponse;
import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
import com.ddicg.erp.modules.cart.model.CartItem;
import com.ddicg.erp.modules.cart.model.ShoppingCart;
import com.ddicg.erp.modules.cart.repository.ShoppingCartRepository;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShoppingCartServiceImpl implements ShoppingCartService {

    ShoppingCartRepository shoppingCartRepository;
    AttributesRepository attributesRepository;
    UserRepository userRepository;
    SecurityUtil securityUtil;

    @Override
    @Transactional
    public Response<ShoppingCartDto> getCart() {
        User user = getCurrentUser();
        ShoppingCart cart = getOrCreateCart(user);
        ShoppingCartDto dto = enrichAndCalculateCart(cart);
        shoppingCartRepository.save(cart);
        return Response.ok(dto);
    }

    @Override
    @Transactional(readOnly = true)
    public Response<Integer> getCartCount() {
        User user = getCurrentUser();
        Optional<ShoppingCart> cartOpt = shoppingCartRepository.findByUser(user);
        if (cartOpt.isEmpty() || cartOpt.get().getItems() == null) {
            return Response.ok(0);
        }
        int totalCount = cartOpt.get().getItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
        return Response.ok(totalCount);
    }

    @Override
    @Transactional
    public Response<ShoppingCartDto> addToCart(final List<CartItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Danh sách sản phẩm không được rỗng");
        }

        User user = getCurrentUser();
        ShoppingCart cart = getOrCreateCart(user);

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

            Optional<CartItem> existingItemOpt = cart.getItems().stream()
                    .filter(ci -> ci.getSku().equals(sku))
                    .findFirst();

            if (existingItemOpt.isPresent()) {
                CartItem existingItem = existingItemOpt.get();
                existingItem.setQuantity(existingItem.getQuantity() + requestedQuantity);
            } else {
                CartItem newItem = CartItem.builder()
                        .cart(cart)
                        .product(attributes.getProduct())
                        .sku(sku)
                        .quantity(requestedQuantity)
                        .build();
                cart.addItem(newItem);
            }
        }

        cart.setLastActivityAt(LocalDateTime.now());
        ShoppingCartDto dto = enrichAndCalculateCart(cart);
        shoppingCartRepository.save(cart);
        log.info("User [{}] đã thêm {} sản phẩm vào giỏ hàng", user.getUsername(), items.size());

        return Response.ok(dto, "Thêm sản phẩm vào giỏ hàng thành công");
    }

    @Override
    @Transactional
    public Response<ShoppingCartDto> updateItemQuantity(final String sku, final Integer quantity) {
        if (sku == null || sku.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "SKU không được để trống");
        }
        if (quantity == null || quantity < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Số lượng không hợp lệ");
        }

        User user = getCurrentUser();
        ShoppingCart cart = getOrCreateCart(user);

        if (quantity == 0) {
            cart.getItems().removeIf(ci -> ci.getSku().equals(sku));
        } else {
            CartItem targetItem = cart.getItems().stream()
                    .filter(ci -> ci.getSku().equals(sku))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Sản phẩm không có trong giỏ hàng"));
            targetItem.setQuantity(quantity);
        }

        cart.setLastActivityAt(LocalDateTime.now());
        ShoppingCartDto dto = enrichAndCalculateCart(cart);
        shoppingCartRepository.save(cart);
        log.info("User [{}] đã cập nhật SKU [{}] với số lượng {}", user.getUsername(), sku, quantity);

        return Response.ok(dto, "Cập nhật số lượng sản phẩm thành công");
    }

    @Override
    @Transactional
    public Response<ShoppingCartDto> removeItem(final String sku) {
        if (sku == null || sku.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "SKU không được để trống");
        }

        User user = getCurrentUser();
        ShoppingCart cart = getOrCreateCart(user);

        boolean removed = cart.getItems().removeIf(ci -> ci.getSku().equals(sku));
        if (!removed) {
            throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Không tìm thấy sản phẩm trong giỏ hàng");
        }

        cart.setLastActivityAt(LocalDateTime.now());
        ShoppingCartDto dto = enrichAndCalculateCart(cart);
        shoppingCartRepository.save(cart);
        log.info("User [{}] đã xóa SKU [{}] khỏi giỏ hàng", user.getUsername(), sku);

        return Response.ok(dto, "Đã xóa sản phẩm khỏi giỏ hàng");
    }

    @Override
    @Transactional
    public Response<ShoppingCartDto> removeItems(final List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Danh sách SKU cần xóa không được rỗng");
        }

        User user = getCurrentUser();
        ShoppingCart cart = getOrCreateCart(user);

        boolean removed = cart.getItems().removeIf(ci -> skus.contains(ci.getSku()));
        if (!removed) {
            throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Không tìm thấy sản phẩm nào để xóa");
        }

        cart.setLastActivityAt(LocalDateTime.now());
        ShoppingCartDto dto = enrichAndCalculateCart(cart);
        shoppingCartRepository.save(cart);
        log.info("User [{}] đã xóa {} sản phẩm khỏi giỏ hàng", user.getUsername(), skus.size());

        return Response.ok(dto, "Đã xóa các sản phẩm được chọn khỏi giỏ hàng");
    }

    @Override
    @Transactional
    public Response<ShoppingCartDto> clearCart() {
        User user = getCurrentUser();
        ShoppingCart cart = getOrCreateCart(user);

        cart.clear();
        cart.setLastActivityAt(LocalDateTime.now());
        ShoppingCartDto dto = enrichAndCalculateCart(cart);
        shoppingCartRepository.save(cart);
        log.info("User [{}] đã xóa toàn bộ giỏ hàng", user.getUsername());

        return Response.ok(dto, "Đã xóa toàn bộ giỏ hàng");
    }

    /**
     * Thuật toán tập trung: Tính toán tổng tiền, tự động dọn dẹp (auto-clean) và làm giàu snapshot DTO
     */
    private ShoppingCartDto enrichAndCalculateCart(ShoppingCart cart) {
        if (cart == null) return null;
        List<CartItem> items = cart.getItems();
        if (items == null || items.isEmpty()) {
            return ShoppingCartDto.builder()
                    .id(cart.getId())
                    .username(cart.getUser() != null ? cart.getUser().getName() : null)
                    .items(List.of())
                    .totalItems(0)
                    .totalPrice(0.0)
                    .totalSalePrice(0.0)
                    .totalDiscount(0.0)
                    .finalAmount(0.0)
                    .build();
        }

        List<String> skus = items.stream().map(CartItem::getSku).distinct().toList();
        Map<String, Attributes> attributesMap = attributesRepository.findAllBySku_skuIn(skus).stream()
                .filter(a -> a.getSku() != null && a.getSku().getSku() != null)
                .collect(Collectors.toMap(a -> a.getSku().getSku(), a -> a, (a1, a2) -> a1));

        // Auto-clean: Tự động xóa sạch item nếu SKU đã bị xóa khỏi hệ thống
        Iterator<CartItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            CartItem item = iterator.next();
            if (!attributesMap.containsKey(item.getSku())) {
                log.warn("Auto-clean: Removing dead/deleted SKU [{}] from cart of user [{}]",
                        item.getSku(), cart.getUser() != null ? cart.getUser().getUsername() : "unknown");
                item.setCart(null);
                iterator.remove();
            }
        }

        List<CartItemResponse> itemResponses = new ArrayList<>();
        double totalPrice = 0.0;
        double totalSalePrice = 0.0;
        int totalItems = 0;

        for (CartItem item : items) {
            Attributes attr = attributesMap.get(item.getSku());
            if (attr == null) continue;

            Product product = attr.getProduct();
            String productName = product != null ? product.getName() : attr.getName();
            String imageUrl = null;
            if (product != null && product.getMediaItems() != null && !product.getMediaItems().isEmpty()) {
                imageUrl = product.getMediaItems().get(0).getUrl();
            }

            double unitPrice = attr.getPrice();
            double salePrice = (attr.getSalePrice() > 0) ? attr.getSalePrice() : unitPrice;
            int qty = item.getQuantity();
            double subTotal = salePrice * qty;

            totalPrice += (unitPrice * qty);
            totalSalePrice += subTotal;
            totalItems += qty;

            boolean isAvailable = (attr.getStatusProduct() == StockStatus.AVAILABLE);

            itemResponses.add(CartItemResponse.builder()
                    .sku(item.getSku())
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
                .id(cart.getId())
                .username(cart.getUser() != null ? cart.getUser().getName() : null)
                .items(itemResponses)
                .totalItems(totalItems)
                .totalPrice(totalPrice)
                .totalSalePrice(totalSalePrice)
                .totalDiscount(totalDiscount)
                .finalAmount(totalSalePrice)
                .build();
    }

    private User getCurrentUser() {
        String username = securityUtil.getCurrentUsername();
        return userRepository.findByNameOrEmail(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User không tồn tại"));
    }

    private ShoppingCart getOrCreateCart(User user) {
        return shoppingCartRepository.findByUser(user)
                .orElseGet(() -> {
                    ShoppingCart newCart = new ShoppingCart(user);
                    return shoppingCartRepository.save(newCart);
                });
    }
}
