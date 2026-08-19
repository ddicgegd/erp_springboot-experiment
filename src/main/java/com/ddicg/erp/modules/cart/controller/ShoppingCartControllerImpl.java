package com.ddicg.erp.modules.cart.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
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
    public Response<ShoppingCartDto> getCart() {
        return shoppingCartService.getCart();
    }

    @Override
    public Response<Integer> getCartCount() {
        return shoppingCartService.getCartCount();
    }

    @Override
    public Response<ShoppingCartDto> addToCart(List<CartItemRequest> items) {
        return shoppingCartService.addToCart(items);
    }

    @Override
    public Response<ShoppingCartDto> addLegacy(List<CartItemRequest> items) {
        return shoppingCartService.addToCart(items);
    }

    @Override
    public Response<ShoppingCartDto> updateItemQuantity(String sku, UpdateCartItemRequest request) {
        return shoppingCartService.updateItemQuantity(sku, request.getQuantity());
    }

    @Override
    public Response<ShoppingCartDto> removeItem(String sku) {
        return shoppingCartService.removeItem(sku);
    }

    @Override
    public Response<ShoppingCartDto> removeItems(List<String> skus) {
        return shoppingCartService.removeItems(skus);
    }

    @Override
    public Response<ShoppingCartDto> clearCart() {
        return shoppingCartService.clearCart();
    }
}
