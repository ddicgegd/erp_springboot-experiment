package com.ddicg.erp.modules.cart.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.model.embedded.MediaItem;
import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
import com.ddicg.erp.modules.cart.model.CartItem;
import com.ddicg.erp.modules.cart.model.ShoppingCart;
import com.ddicg.erp.modules.cart.repository.ShoppingCartRepository;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.merchandise.model.Product;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoppingCartServiceTest {

    @Mock
    private ShoppingCartRepository shoppingCartRepository;

    @Mock
    private AttributesRepository attributesRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityUtil securityUtil;

    @InjectMocks
    private ShoppingCartServiceImpl shoppingCartService;

    private User mockUser;
    private Product mockProduct;
    private Attributes mockAttr1;
    private Attributes mockAttr2;
    private ShoppingCart mockCart;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .name("testuser")
                .fullName("Test User")
                .email("test@example.com")
                .password("password")
                .build();
        mockUser.setId(1L);

        mockProduct = Product.builder()
                .name("Áo Thun Nam Cao Cấp")
                .mediaItems(List.of(MediaItem.builder().url("http://example.com/aothun.jpg").build()))
                .build();
        mockProduct.setId(10L);

        mockAttr1 = Attributes.builder()
                .sku(new SkuInfo("SKU-AO-DEN-L"))
                .name("Màu Đen - Size L")
                .price(150000.0)
                .salePrice(120000.0)
                .statusProduct(StockStatus.AVAILABLE)
                .product(mockProduct)
                .build();
        mockAttr1.setId(101L);

        mockAttr2 = Attributes.builder()
                .sku(new SkuInfo("SKU-AO-TRANG-M"))
                .name("Màu Trắng - Size M")
                .price(200000.0)
                .salePrice(0.0) // Không sale
                .statusProduct(StockStatus.AVAILABLE)
                .product(mockProduct)
                .build();
        mockAttr2.setId(102L);

        mockCart = new ShoppingCart(mockUser);
        mockCart.setId(100L);

        lenient().when(securityUtil.getCurrentUsername()).thenReturn("testuser");
        lenient().when(userRepository.findByNameOrEmail("testuser")).thenReturn(Optional.of(mockUser));
        lenient().when(shoppingCartRepository.findByUser(mockUser)).thenReturn(Optional.of(mockCart));
        lenient().when(shoppingCartRepository.save(any(ShoppingCart.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Thêm sản phẩm mới vào giỏ hàng -> Tạo mới CartItem và tính đúng tổng tiền")
    void addToCart_newSku_shouldAddItemAndComputeTotals() {
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-AO-DEN-L"))).thenReturn(List.of(mockAttr1));

        CartItemRequest request = CartItemRequest.builder()
                .sku("SKU-AO-DEN-L")
                .quantity(2)
                .build();

        Response<ShoppingCartDto> response = shoppingCartService.addToCart(List.of(request));

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        ShoppingCartDto dto = response.getData();
        assertNotNull(dto);
        assertEquals(2, dto.getTotalItems());
        assertEquals(300000.0, dto.getTotalPrice()); // 150k * 2
        assertEquals(240000.0, dto.getTotalSalePrice()); // 120k * 2
        assertEquals(60000.0, dto.getTotalDiscount()); // 300k - 240k
        assertEquals(240000.0, dto.getFinalAmount());

        assertEquals(1, dto.getItems().size());
        assertEquals("SKU-AO-DEN-L", dto.getItems().get(0).getSku());
        assertEquals("Áo Thun Nam Cao Cấp", dto.getItems().get(0).getProductName());
        assertEquals(2, dto.getItems().get(0).getQuantity());
    }

    @Test
    @DisplayName("Thêm sản phẩm đã có trong giỏ -> Tự động cộng dồn số lượng")
    void addToCart_existingSku_shouldAccumulateQuantity() {
        CartItem existingItem = CartItem.builder()
                .cart(mockCart)
                .product(mockProduct)
                .sku("SKU-AO-DEN-L")
                .quantity(2)
                .build();
        mockCart.addItem(existingItem);

        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-AO-DEN-L"))).thenReturn(List.of(mockAttr1));

        CartItemRequest request = CartItemRequest.builder()
                .sku("SKU-AO-DEN-L")
                .quantity(3)
                .build();

        Response<ShoppingCartDto> response = shoppingCartService.addToCart(List.of(request));

        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(5, dto.getTotalItems()); // 2 + 3 = 5
        assertEquals(600000.0, dto.getFinalAmount()); // 120k * 5
    }

    @Test
    @DisplayName("Thêm SKU không tồn tại -> Ném BusinessException")
    void addToCart_invalidSku_shouldThrowException() {
        when(attributesRepository.findAllBySku_skuIn(List.of("INVALID-SKU"))).thenReturn(List.of());

        CartItemRequest request = CartItemRequest.builder()
                .sku("INVALID-SKU")
                .quantity(1)
                .build();

        assertThrows(BusinessException.class, () -> shoppingCartService.addToCart(List.of(request)));
    }

    @Test
    @DisplayName("Lấy giỏ hàng có chứa SKU đã bị xóa khỏi hệ thống -> Tự động dọn dẹp (Auto-Clean)")
    void getCart_shouldAutoCleanDeletedSkus() {
        CartItem validItem = CartItem.builder().cart(mockCart).sku("SKU-AO-DEN-L").quantity(1).build();
        CartItem deadItem = CartItem.builder().cart(mockCart).sku("DEAD-SKU").quantity(1).build();
        mockCart.addItem(validItem);
        mockCart.addItem(deadItem);

        // Chỉ mockAttr1 tồn tại trong DB, DEAD-SKU không còn
        when(attributesRepository.findAllBySku_skuIn(anyList())).thenReturn(List.of(mockAttr1));

        Response<ShoppingCartDto> response = shoppingCartService.getCart();

        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(1, dto.getItems().size());
        assertEquals("SKU-AO-DEN-L", dto.getItems().get(0).getSku());
        assertEquals(1, mockCart.getItems().size()); // DEAD-SKU đã bị xóa khỏi collection
    }

    @Test
    @DisplayName("Cập nhật số lượng trực tiếp cho 1 SKU -> Cập nhật đúng và tính lại tổng tiền")
    void updateItemQuantity_shouldUpdateAndRecalculate() {
        CartItem item = CartItem.builder().cart(mockCart).sku("SKU-AO-DEN-L").quantity(1).build();
        mockCart.addItem(item);

        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-AO-DEN-L"))).thenReturn(List.of(mockAttr1));

        Response<ShoppingCartDto> response = shoppingCartService.updateItemQuantity("SKU-AO-DEN-L", 4);

        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(4, dto.getTotalItems());
        assertEquals(480000.0, dto.getFinalAmount()); // 120k * 4
    }

    @Test
    @DisplayName("Cập nhật số lượng về 0 -> Tự động xóa item khỏi giỏ")
    void updateItemQuantity_zero_shouldRemoveItem() {
        CartItem item = CartItem.builder().cart(mockCart).sku("SKU-AO-DEN-L").quantity(1).build();
        mockCart.addItem(item);

        Response<ShoppingCartDto> response = shoppingCartService.updateItemQuantity("SKU-AO-DEN-L", 0);

        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(0, dto.getTotalItems());
        assertTrue(dto.getItems().isEmpty());
    }

    @Test
    @DisplayName("Xóa 1 sản phẩm theo SKU -> Xóa thành công")
    void removeItem_shouldRemoveFromCart() {
        CartItem item1 = CartItem.builder().cart(mockCart).sku("SKU-AO-DEN-L").quantity(1).build();
        CartItem item2 = CartItem.builder().cart(mockCart).sku("SKU-AO-TRANG-M").quantity(2).build();
        mockCart.addItem(item1);
        mockCart.addItem(item2);

        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-AO-TRANG-M"))).thenReturn(List.of(mockAttr2));

        Response<ShoppingCartDto> response = shoppingCartService.removeItem("SKU-AO-DEN-L");

        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(1, dto.getItems().size());
        assertEquals("SKU-AO-TRANG-M", dto.getItems().get(0).getSku());
    }

    @Test
    @DisplayName("Xóa toàn bộ giỏ hàng -> Giỏ hàng trống rỗng")
    void clearCart_shouldEmptyCart() {
        CartItem item = CartItem.builder().cart(mockCart).sku("SKU-AO-DEN-L").quantity(1).build();
        mockCart.addItem(item);

        Response<ShoppingCartDto> response = shoppingCartService.clearCart();

        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(0, dto.getTotalItems());
        assertTrue(dto.getItems().isEmpty());
    }

    @Test
    @DisplayName("Lấy nhanh số lượng item trong giỏ (Cart Count Badge) -> Trả về tổng số lượng")
    void getCartCount_shouldReturnCorrectItemSum() {
        CartItem item1 = CartItem.builder().cart(mockCart).sku("SKU-AO-DEN-L").quantity(3).build();
        CartItem item2 = CartItem.builder().cart(mockCart).sku("SKU-AO-TRANG-M").quantity(2).build();
        mockCart.addItem(item1);
        mockCart.addItem(item2);

        Response<Integer> response = shoppingCartService.getCartCount();

        assertNotNull(response);
        assertEquals(5, response.getData()); // 3 + 2 = 5
    }
}
