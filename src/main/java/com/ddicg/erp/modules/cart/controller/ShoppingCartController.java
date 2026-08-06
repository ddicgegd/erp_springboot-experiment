package com.ddicg.erp.modules.cart.controller;

import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.core.common.dto.response.Response;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/cart")
public interface ShoppingCartController {

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    Response<ShoppingCartDto> getCart();

    @PostMapping("/add")
    @ResponseStatus(HttpStatus.OK)
    Response<ShoppingCartDto> addToCart(@Valid @RequestBody List<CartItemRequest> items);

    @DeleteMapping("/remove")
    @ResponseStatus(HttpStatus.OK)
    Response<ShoppingCartDto> removeFromCart(@RequestBody List<String> skus);

    @DeleteMapping("/clear")
    @ResponseStatus(HttpStatus.OK)
    Response<ShoppingCartDto> clearCart();
}
