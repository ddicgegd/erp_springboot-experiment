package com.ddicg.erp.modules.cart.controller;

import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.cart.service.iShoppingCart;
import com.ddicg.erp.modules.cart.controller.ShoppingCartController;
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
