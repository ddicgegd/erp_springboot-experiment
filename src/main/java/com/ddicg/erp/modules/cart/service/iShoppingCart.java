package com.ddicg.erp.modules.cart.service;

import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.core.common.dto.response.Response;

import java.util.List;

public interface iShoppingCart {

    Response<ShoppingCartDto> getCart();
    Response<ShoppingCartDto> add(final List<CartItemRequest> items);
    Response<ShoppingCartDto> remove(final List<String> skus);
    Response<ShoppingCartDto> clearCart();
}
