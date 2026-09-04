package com.ddicg.erp.modules.cart.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.modules.cart.dto.MergeCartRequest;
import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
import com.ddicg.erp.modules.cart.dto.UpdateCartItemRequest;
import com.ddicg.erp.modules.cart.service.ShoppingCartService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShoppingCartControllerImpl implements ShoppingCartController {

    ShoppingCartService shoppingCartService;

    @Override
    public Response<ShoppingCartDto> getCart(List<String> fields, List<String> include, String guestId) {
        return shoppingCartService.getCart(guestId, fields, include);
    }

    @Override
    public Response<Integer> getCartCount(String guestId) {
        return shoppingCartService.getCartCount(guestId);
    }

    @Override
    public Response<ShoppingCartDto> addToCart(List<CartItemRequest> items, String guestId) {
        return shoppingCartService.addToCart(items, guestId);
    }

    @Override
    public Response<ShoppingCartDto> addLegacy(List<CartItemRequest> items, String guestId) {
        return shoppingCartService.addToCart(items, guestId);
    }

    @Override
    public Response<ShoppingCartDto> updateItemQuantity(String sku, UpdateCartItemRequest request, String guestId) {
        return shoppingCartService.updateItemQuantity(sku, request.getQuantity(), guestId);
    }

    @Override
    public Response<ShoppingCartDto> removeItem(String sku, String guestId) {
        return shoppingCartService.removeItem(sku, guestId);
    }

    @Override
    public Response<ShoppingCartDto> removeItems(List<String> skus, String guestId) {
        return shoppingCartService.removeItems(skus, guestId);
    }

    @Override
    public Response<ShoppingCartDto> clearCart(String guestId) {
        return shoppingCartService.clearCart(guestId);
    }

    @Override
    public Response<ShoppingCartDto> mergeCart(MergeCartRequest request, String guestIdHeader) {
        String guestId = (request != null && request.getGuestId() != null && !request.getGuestId().isBlank())
                ? request.getGuestId()
                : guestIdHeader;
        return shoppingCartService.mergeCart(guestId);
    }
}
