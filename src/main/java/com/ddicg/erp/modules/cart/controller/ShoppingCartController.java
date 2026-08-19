package com.ddicg.erp.modules.cart.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
import com.ddicg.erp.modules.cart.dto.UpdateCartItemRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/cart")
public interface ShoppingCartController {

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<ShoppingCartDto> getCart();

    @GetMapping("/count")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<Integer> getCartCount();

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<ShoppingCartDto> addToCart(@Valid @RequestBody List<CartItemRequest> items);

    @PostMapping("/add")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<ShoppingCartDto> addLegacy(@Valid @RequestBody List<CartItemRequest> items);

    @PutMapping("/items/{sku}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<ShoppingCartDto> updateItemQuantity(
            @PathVariable String sku,
            @Valid @RequestBody UpdateCartItemRequest request);

    @DeleteMapping("/items/{sku}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<ShoppingCartDto> removeItem(@PathVariable String sku);

    @DeleteMapping("/remove")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<ShoppingCartDto> removeItems(@RequestBody List<String> skus);

    @DeleteMapping("/clear")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<ShoppingCartDto> clearCart();
}
