package com.ddicg.erp.modules.cart.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.modules.cart.dto.MergeCartRequest;
import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
import com.ddicg.erp.modules.cart.dto.UpdateCartItemRequest;
import com.ddicg.erp.modules.cart.service.ShoppingCartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoppingCartControllerTest {

    @Mock
    private ShoppingCartService shoppingCartService;

    @InjectMocks
    private ShoppingCartControllerImpl shoppingCartController;

    private ShoppingCartDto mockCartDto;

    @BeforeEach
    void setUp() {
        mockCartDto = ShoppingCartDto.builder()
                .username("testuser")
                .totalItems(2)
                .finalAmount(150000.0)
                .build();
    }

    @Test
    @DisplayName("GET /api/cart -> Gọi shoppingCartService.getCart với đúng tham số fields và include")
    void getCart_shouldDelegateToService() {
        List<String> fields = List.of("totalItems", "username");
        List<String> include = List.of("specifications");
        when(shoppingCartService.getCart("guest-123", fields, include)).thenReturn(Response.ok(mockCartDto));

        Response<ShoppingCartDto> response = shoppingCartController.getCart(fields, include, "guest-123");

        verify(shoppingCartService).getCart("guest-123", fields, include);
        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("testuser", response.getData().getUsername());
    }

    @Test
    @DisplayName("GET /api/cart/count -> Gọi shoppingCartService.getCartCount")
    void getCartCount_shouldDelegateToService() {
        when(shoppingCartService.getCartCount("guest-123")).thenReturn(Response.ok(5));

        Response<Integer> response = shoppingCartController.getCartCount("guest-123");

        verify(shoppingCartService).getCartCount("guest-123");
        assertNotNull(response);
        assertEquals(5, response.getData());
    }

    @Test
    @DisplayName("POST /api/cart/items -> Gọi shoppingCartService.addToCart")
    void addToCart_shouldDelegateToService() {
        List<CartItemRequest> items = List.of(CartItemRequest.builder().sku("SKU-1").quantity(2).build());
        when(shoppingCartService.addToCart(items, "guest-123")).thenReturn(Response.ok(mockCartDto));

        Response<ShoppingCartDto> response = shoppingCartController.addToCart(items, "guest-123");

        verify(shoppingCartService).addToCart(items, "guest-123");
        assertNotNull(response);
        assertEquals(mockCartDto, response.getData());
    }

    @Test
    @DisplayName("PUT /api/cart/items/{sku} -> Gọi shoppingCartService.updateItemQuantity")
    void updateItemQuantity_shouldDelegateToService() {
        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(3).build();
        when(shoppingCartService.updateItemQuantity("SKU-1", 3, "guest-123")).thenReturn(Response.ok(mockCartDto));

        Response<ShoppingCartDto> response = shoppingCartController.updateItemQuantity("SKU-1", request, "guest-123");

        verify(shoppingCartService).updateItemQuantity("SKU-1", 3, "guest-123");
        assertNotNull(response);
        assertEquals(mockCartDto, response.getData());
    }

    @Test
    @DisplayName("DELETE /api/cart/items/{sku} -> Gọi shoppingCartService.removeItem")
    void removeItem_shouldDelegateToService() {
        when(shoppingCartService.removeItem("SKU-1", "guest-123")).thenReturn(Response.ok(mockCartDto));

        Response<ShoppingCartDto> response = shoppingCartController.removeItem("SKU-1", "guest-123");

        verify(shoppingCartService).removeItem("SKU-1", "guest-123");
        assertNotNull(response);
        assertEquals(mockCartDto, response.getData());
    }

    @Test
    @DisplayName("DELETE /api/cart (không truyền skus) -> Gọi shoppingCartService.clearCart để xóa toàn bộ giỏ")
    void deleteCart_withoutSkus_shouldClearEntireCart() {
        when(shoppingCartService.clearCart("guest-123")).thenReturn(Response.ok(mockCartDto));

        Response<ShoppingCartDto> response = shoppingCartController.deleteCart(null, "guest-123");

        verify(shoppingCartService).clearCart("guest-123");
        verify(shoppingCartService, never()).removeItems(anyList(), any());
        assertNotNull(response);
        assertEquals(mockCartDto, response.getData());
    }

    @Test
    @DisplayName("DELETE /api/cart?skus=SKU-1,SKU-2 -> Gọi shoppingCartService.removeItems để xóa batch")
    void deleteCart_withSkus_shouldRemoveSpecifiedItems() {
        List<String> skus = List.of("SKU-1", "SKU-2");
        when(shoppingCartService.removeItems(skus, "guest-123")).thenReturn(Response.ok(mockCartDto));

        Response<ShoppingCartDto> response = shoppingCartController.deleteCart(skus, "guest-123");

        verify(shoppingCartService).removeItems(skus, "guest-123");
        verify(shoppingCartService, never()).clearCart(any());
        assertNotNull(response);
        assertEquals(mockCartDto, response.getData());
    }

    @Test
    @DisplayName("POST /api/cart/merge -> Ưu tiên guestId từ body MergeCartRequest nếu có")
    void mergeCart_withBodyGuestId_shouldUseBodyId() {
        MergeCartRequest request = MergeCartRequest.builder().guestId("body-guest-id").build();
        when(shoppingCartService.mergeCart("body-guest-id")).thenReturn(Response.ok(mockCartDto));

        Response<ShoppingCartDto> response = shoppingCartController.mergeCart(request, "header-guest-id");

        verify(shoppingCartService).mergeCart("body-guest-id");
        assertNotNull(response);
        assertEquals(mockCartDto, response.getData());
    }

    @Test
    @DisplayName("POST /api/cart/merge -> Lấy guestId từ Header nếu body rỗng")
    void mergeCart_withHeaderGuestId_shouldUseHeaderId() {
        when(shoppingCartService.mergeCart("header-guest-id")).thenReturn(Response.ok(mockCartDto));

        Response<ShoppingCartDto> response = shoppingCartController.mergeCart(null, "header-guest-id");

        verify(shoppingCartService).mergeCart("header-guest-id");
        assertNotNull(response);
        assertEquals(mockCartDto, response.getData());
    }
}
