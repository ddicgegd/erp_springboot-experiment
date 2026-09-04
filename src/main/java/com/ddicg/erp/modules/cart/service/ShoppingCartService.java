package com.ddicg.erp.modules.cart.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;

import java.util.List;

public interface ShoppingCartService {

    Response<ShoppingCartDto> getCart(String guestId, List<String> fields, List<String> include);

    default Response<ShoppingCartDto> getCart(String guestId) {
        return getCart(guestId, null, null);
    }

    default Response<ShoppingCartDto> getCart() {
        return getCart(null, null, null);
    }

    Response<Integer> getCartCount(String guestId);

    default Response<Integer> getCartCount() {
        return getCartCount(null);
    }

    Response<ShoppingCartDto> addToCart(List<CartItemRequest> items, String guestId);

    default Response<ShoppingCartDto> addToCart(List<CartItemRequest> items) {
        return addToCart(items, null);
    }

    Response<ShoppingCartDto> updateItemQuantity(String sku, Integer quantity, String guestId);

    default Response<ShoppingCartDto> updateItemQuantity(String sku, Integer quantity) {
        return updateItemQuantity(sku, quantity, null);
    }

    Response<ShoppingCartDto> removeItem(String sku, String guestId);

    default Response<ShoppingCartDto> removeItem(String sku) {
        return removeItem(sku, null);
    }

    Response<ShoppingCartDto> removeItems(List<String> skus, String guestId);

    default Response<ShoppingCartDto> removeItems(List<String> skus) {
        return removeItems(skus, null);
    }

    Response<ShoppingCartDto> clearCart(String guestId);

    default Response<ShoppingCartDto> clearCart() {
        return clearCart(null);
    }

    Response<ShoppingCartDto> mergeCart(String guestId);
}
