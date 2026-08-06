package com.ddicg.erp.service.interfaces;

import com.ddicg.erp.service.dto.ShoppingCartDto;
import com.ddicg.erp.service.dto.request.CartItemRequest;
import com.ddicg.erp.service.dto.response.ResponseConfig.Response;

import java.util.List;

public interface iShoppingCart {

    Response<ShoppingCartDto> getCart();
    Response<ShoppingCartDto> add(final List<CartItemRequest> items);
    Response<ShoppingCartDto> remove(final List<String> skus);
    Response<ShoppingCartDto> clearCart();
}
