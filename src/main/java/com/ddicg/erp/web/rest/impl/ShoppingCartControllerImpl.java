package com.ddicg.erp.web.rest.impl;

import com.ddicg.erp.service.dto.ShoppingCartDto;
import com.ddicg.erp.service.dto.request.CartItemRequest;
import com.ddicg.erp.service.dto.response.ResponseConfig.Response;
import com.ddicg.erp.service.interfaces.iShoppingCart;
import com.ddicg.erp.web.rest.ShoppingCartController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ShoppingCartControllerImpl implements ShoppingCartController {

    private final iShoppingCart shoppingCartService;

    @Override
    public Response<ShoppingCartDto> getCart() {
        return shoppingCartService.getCart();
    }

    @Override
    public Response<ShoppingCartDto> addToCart(final List<CartItemRequest> items) {
        return shoppingCartService.add(items);
    }

    @Override
    public Response<ShoppingCartDto> removeFromCart(final List<String> skus) {
        return shoppingCartService.remove(skus);
    }

    @Override
    public Response<ShoppingCartDto> clearCart() {
        return shoppingCartService.clearCart();
    }
}
