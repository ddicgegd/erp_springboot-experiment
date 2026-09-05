package com.ddicg.erp.modules.cart.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.modules.cart.dto.MergeCartRequest;
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
    Response<ShoppingCartDto> getCart(
            @RequestParam(value = "fields", required = false) List<String> fields,
            @RequestParam(value = "include", required = false) List<String> include,
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId);

    @GetMapping("/count")
    @ResponseStatus(HttpStatus.OK)
    Response<Integer> getCartCount(
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId);

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    Response<ShoppingCartDto> addToCart(
            @Valid @RequestBody List<CartItemRequest> items,
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId);

    @PutMapping("/items/{sku}")
    @ResponseStatus(HttpStatus.OK)
    Response<ShoppingCartDto> updateItemQuantity(
            @PathVariable String sku,
            @Valid @RequestBody UpdateCartItemRequest request,
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId);

    @DeleteMapping("/items/{sku}")
    @ResponseStatus(HttpStatus.OK)
    Response<ShoppingCartDto> removeItem(
            @PathVariable String sku,
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId);

    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    Response<ShoppingCartDto> deleteCart(
            @RequestParam(value = "skus", required = false) List<String> skus,
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId);

    @PostMapping("/merge")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<ShoppingCartDto> mergeCart(
            @RequestBody(required = false) MergeCartRequest request,
            @RequestHeader(value = "X-Guest-Id", required = false) String guestIdHeader);
}
