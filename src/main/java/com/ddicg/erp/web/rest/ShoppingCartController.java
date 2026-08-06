package com.ddicg.erp.web.rest;

import com.ddicg.erp.service.dto.ShoppingCartDto;
import com.ddicg.erp.service.dto.request.CartItemRequest;
import com.ddicg.erp.service.dto.response.ResponseConfig.Response;
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
