package com.ddicg.erp.modules.merchandise.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.cart.model.ShoppingCart;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import com.ddicg.erp.modules.cart.repository.ShoppingCartRepository;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.cart.service.iShoppingCart;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShoppingCartService implements iShoppingCart {


    private final ShoppingCartRepository shoppingCartRepository;
    private final AttributesRepository attributesRepository;
    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;
    private final Helper helper;

    @Override
    @Transactional(readOnly = true)
    public Response<ShoppingCartDto> getCart() {
        String username = securityUtil.getCurrentUsername();
        User user = userRepository.findByNameOrEmail(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User không tồn tại"));

        ShoppingCart cart = shoppingCartRepository.findByUser(user)
                .orElseGet(() -> helper.createNewCart(user));

        return Response.ok(helper.toDto(cart));
    }

    @Override
    @Transactional
    public Response<ShoppingCartDto> add(final List<CartItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Danh sách sản phẩm không được rỗng");
        }

        String username = securityUtil.getCurrentUsername();
        User user = userRepository.findByNameOrEmail(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User không tồn tại"));

        ShoppingCart cart = shoppingCartRepository.findByUser(user)
                .orElseGet(() -> helper.createNewCart(user));

        List<String> skus = items.stream()
                .map(CartItemRequest::getSku)
                .distinct()
                .toList();

        Map<String, Attributes> attributesMap = attributesRepository
                .findAllBySku_skuIn(skus)
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getSku().getSku(),
                        a -> a));

        for (CartItemRequest item : items) {
            String sku = item.getSku();
            int quantity = item.getQuantity();

            Attributes attributes = attributesMap.get(sku);
            if (attributes == null) {
                throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND,
                        "Sản phẩm " + sku + " không tồn tại");
            }

            if (quantity == 0) {
                cart.removeItemBySku(sku);
                log.info("Đã xóa sản phẩm {} khỏi giỏ hàng của user {}", sku, username);

            } else if (quantity > 0) {
                helper.handleAddItem(cart, sku, quantity, attributes);

            } else {
                helper.handleDecreaseItem(cart, sku, Math.abs(quantity));
            }
        }

        helper.recalculateAndUpdateTotals(cart);
        cart.getAuditInfo().addUpdateEntry("Cập nhật giỏ hàng", username);

        ShoppingCart savedCart = shoppingCartRepository.save(cart);
        log.info("User {} đã cập nhật giỏ hàng với {} items", username, items.size());

        return Response.ok(
                helper.toDto(savedCart),
                "Cập nhật giỏ hàng thành công");
    }

    @Override
    @Transactional
    public Response<ShoppingCartDto> remove(final List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Danh sách sản phẩm cần xóa không được rỗng");
        }

        String username = securityUtil.getCurrentUsername();
        User user = userRepository.findByNameOrEmail(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User không tồn tại"));

        ShoppingCart cart = shoppingCartRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Giỏ hàng không tồn tại"));

        int removedCount = 0;
        for (String sku : skus) {
            boolean removed = cart.getCartItems().removeIf(ci -> ci.getSku().equals(sku));
            if (removed) removedCount++;
        }

        if (removedCount == 0) {
            throw new BusinessException(ErrorCode.ATTRIBUTES_NOT_FOUND, "Không tìm thấy sản phẩm nào để xóa");
        }

        helper.recalculateAndUpdateTotals(cart);
        cart.getAuditInfo().addUpdateEntry("Xóa sản phẩm khỏi giỏ hàng", username);

        ShoppingCart savedCart = shoppingCartRepository.save(cart);
        log.info("User {} đã xóa {} sản phẩm khỏi giỏ hàng", username, removedCount);

        return Response.ok(
                helper.toDto(savedCart),
                String.format("Đã xóa %d sản phẩm khỏi giỏ hàng", removedCount));
    }

    @Override
    @Transactional
    public Response<ShoppingCartDto> clearCart() {
        String username = securityUtil.getCurrentUsername();
        User user = userRepository.findByNameOrEmail(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User không tồn tại"));

        ShoppingCart cart = shoppingCartRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Giỏ hàng không tồn tại"));

        cart.clearItems();
        helper.recalculateAndUpdateTotals(cart);
        cart.getAuditInfo().addUpdateEntry("Xóa toàn bộ giỏ hàng", username);

        ShoppingCart savedCart = shoppingCartRepository.save(cart);
        return Response.ok(helper.toDto(savedCart), "Đã xóa toàn bộ giỏ hàng");
    }
}