package com.ddicg.erp.modules.cart.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;

import java.util.List;

public interface ShoppingCartService {

    Response<ShoppingCartDto> getCart();

    Response<Integer> getCartCount();

    Response<ShoppingCartDto> addToCart(List<CartItemRequest> items);

    Response<ShoppingCartDto> updateItemQuantity(String sku, Integer quantity);

    Response<ShoppingCartDto> removeItem(String sku);

    Response<ShoppingCartDto> removeItems(List<String> skus);

    Response<ShoppingCartDto> clearCart();
}
