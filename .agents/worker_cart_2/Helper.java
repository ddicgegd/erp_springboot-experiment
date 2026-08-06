package com.ddicg.erp.service.Merchandise;

import com.ddicg.erp.model.embedded.AuditInfo;
import com.ddicg.erp.model.entity.Attributes;
import com.ddicg.erp.model.entity.CartItem;
import com.ddicg.erp.model.entity.ShoppingCart;
import com.ddicg.erp.model.entity.User;
import com.ddicg.erp.repository.AttributesRepository;
import com.ddicg.erp.repository.ShoppingCartRepository;
import com.ddicg.erp.service.dto.ShoppingCartDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import com.ddicg.erp.web.rest.error.BusinessException;
import com.ddicg.erp.web.rest.error.ErrorCode;

@Component("featureMerchandiseHelper")
@Slf4j
@RequiredArgsConstructor
public class Helper {

    private final AttributesRepository attributesRepository;
    private final ShoppingCartRepository shoppingCartRepository;
    private static final String ALPHANUMERIC_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    // ─── Cart helpers ───

    public void handleAddItem(ShoppingCart cart, String sku, int quantityToAdd, Attributes attributes) {
        cart.addItem(sku, quantityToAdd);
        log.debug("Đã thêm/cập nhật sản phẩm {} với số lượng {} (tổng cộng)", sku, quantityToAdd);
    }

    public void handleDecreaseItem(ShoppingCart cart, String sku, int quantityToDecrease) {
        cart.getCartItems().stream()
                .filter(ci -> ci.getSku().equals(sku))
                .findFirst()
                .ifPresentOrElse(ci -> {
                    int newQty = ci.getQuantity() - quantityToDecrease;
                    if (newQty <= 0) {
                        cart.removeItemBySku(sku);
                        log.debug("Xóa sản phẩm {} khỏi giỏ hàng do số lượng <= 0", sku);
                    } else {
                        ci.setQuantity(newQty);
                        log.debug("Giảm số lượng sản phẩm {} xuống {}", sku, newQty);
                    }
                }, () -> {
                    throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Sản phẩm " + sku + " không có trong giỏ hàng");
                });
    }

    public void recalculateAndUpdateTotals(ShoppingCart cart) {
        List<CartItem> items = cart.getCartItems();
        if (items == null || items.isEmpty()) {
            cart.updateTotals(0, 0.0, 0.0);
            return;
        }

        List<String> skus = items.stream()
                .map(CartItem::getSku)
                .toList();

        Map<String, Attributes> attributesMap = attributesRepository
                .findAllBySku_skuIn(skus)
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getSku().getSku(),
                        a -> a));

        items.removeIf(item -> !attributesMap.containsKey(item.getSku()));

        int totalItems = items.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();

        double totalPrice = items.stream()
                .mapToDouble(item -> {
                    Attributes a = attributesMap.get(item.getSku());
                    return a != null ? a.getPrice() * item.getQuantity() : 0.0;
                })
                .sum();

        double totalSalePrice = items.stream()
                .mapToDouble(item -> {
                    Attributes a = attributesMap.get(item.getSku());
                    if (a != null) {
                        Double sp = a.getSalePrice();
                        return ((sp != null && sp > 0) ? sp : a.getPrice()) * item.getQuantity();
                    }
                    return 0.0;
                })
                .sum();

        cart.updateTotals(totalItems, totalPrice, totalSalePrice);
    }

    public ShoppingCart createNewCart(User user) {
        ShoppingCart cart = new ShoppingCart();
        cart.setUser(user);
        cart.setAuditInfo(new AuditInfo());
        cart.getAuditInfo().setCreatedAt(LocalDateTime.now());
        cart.getAuditInfo().setCreatedBy(user.getUsername());
        log.info("Tạo giỏ hàng mới cho user: {}", user.getUsername());
        return cart;
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ShoppingCart getOrCreateCart(User user, com.ddicg.erp.repository.ShoppingCartRepository repo) {
        return repo.findByUser(user).orElseGet(() -> {
            try {
                return repo.saveAndFlush(createNewCart(user));
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                return repo.findByUser(user).orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "Error creating cart"));
            }
        });
    }

    public ShoppingCartDto toDto(ShoppingCart cart) {
        if (cart == null) return null;
        List<ShoppingCartDto.CartItemDto> itemDtos = cart.getCartItems() == null ? List.of()
                : cart.getCartItems().stream()
                        .map(ci -> new ShoppingCartDto.CartItemDto(ci.getSku(), ci.getQuantity()))
                        .toList();
        return new ShoppingCartDto(
                cart.getUser() != null ? cart.getUser().getName() : null,
                itemDtos,
                cart.getTotalItems(),
                cart.getTotalPrice(),
                cart.getTotalSalePrice(),
                cart.getTotalDiscount());
    }

    // ─── General helpers ───

    UUID convertStringToUUID(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID không được để trống.");
        }
        String text = id.trim().replace("[", "").replace("]", "").replace("\"", "");
        if (text.length() == 36 && text.chars().filter(c -> c == '-').count() == 4) {
            try { return UUID.fromString(text); } catch (IllegalArgumentException e) {}
        }
        if (text.length() == 32 && !text.contains("-")) {
            return buildUUIDFromHex(text);
        }
        String raw = text.replace("-", "");
        if (raw.length() != 32) {
            throw new IllegalArgumentException(String.format(
                    "Định dạng ID sai. Mong đợi 32 ký tự hex hoặc chuẩn UUID 36 ký tự, nhận được: %d ký tự.", text.length()));
        }
        return buildUUIDFromHex(raw);
    }

    private UUID buildUUIDFromHex(String hex) {
        if (hex.length() != 32) throw new IllegalArgumentException("Chuỗi hex phải có đúng 32 ký tự.");
        if (!hex.matches("[0-9a-fA-F]+")) throw new IllegalArgumentException("ID chứa ký tự không hợp lệ.");
        String formatted = String.format("%s-%s-%s-%s-%s",
                hex.substring(0, 8), hex.substring(8, 12), hex.substring(12, 16),
                hex.substring(16, 20), hex.substring(20, 32));
        return UUID.fromString(formatted);
    }

    public String generateKey() {
        StringBuilder sb = new StringBuilder(5);
        for (int i = 0; i < 5; i++) {
            sb.append(ALPHANUMERIC_CHARACTERS.charAt(
                    ThreadLocalRandom.current().nextInt(ALPHANUMERIC_CHARACTERS.length())));
        }
        return sb.toString();
    }

    List<String> filterBlank(List<String> list) {
        if (list == null) return List.of();
        return list.stream().filter(s -> s != null && !s.isBlank()).toList();
    }
}
