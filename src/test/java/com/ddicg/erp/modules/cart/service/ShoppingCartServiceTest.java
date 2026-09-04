package com.ddicg.erp.modules.cart.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.model.embedded.MediaItem;
import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoppingCartServiceTest {

    @Mock
    private RedisService redisService;

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
    private String cartKey;

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

        cartKey = "cart:items:1";

        lenient().when(securityUtil.getCurrentUsername()).thenReturn("testuser");
        lenient().when(userRepository.findByNameOrEmail("testuser")).thenReturn(Optional.of(mockUser));
    }

    @Test
    @DisplayName("Thêm sản phẩm mới vào giỏ hàng -> Gọi hIncrBy trên Redis và tính đúng tổng tiền")
    void addToCart_newSku_shouldAddItemAndComputeTotals() {
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-AO-DEN-L"))).thenReturn(List.of(mockAttr1));

        Map<Object, Object> cartData = new HashMap<>();
        cartData.put("SKU-AO-DEN-L", "2");
        when(redisService.hGetAll(cartKey)).thenReturn(cartData);

        CartItemRequest request = CartItemRequest.builder()
                .sku("SKU-AO-DEN-L")
                .quantity(2)
                .build();

        Response<ShoppingCartDto> response = shoppingCartService.addToCart(List.of(request));

        verify(redisService).hIncrBy(cartKey, "SKU-AO-DEN-L", 2);
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
        Map<Object, Object> cartData = new HashMap<>();
        cartData.put("SKU-AO-DEN-L", "1");
        cartData.put("DEAD-SKU", "1");
        when(redisService.hGetAll(cartKey)).thenReturn(cartData);

        // Chỉ mockAttr1 tồn tại trong DB, DEAD-SKU không còn
        when(attributesRepository.findAllBySku_skuIn(anyList())).thenReturn(List.of(mockAttr1));

        Response<ShoppingCartDto> response = shoppingCartService.getCart();

        verify(redisService).hDelete(cartKey, "DEAD-SKU");
        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(1, dto.getItems().size());
        assertEquals("SKU-AO-DEN-L", dto.getItems().get(0).getSku());
    }

    @Test
    @DisplayName("Cập nhật số lượng trực tiếp cho 1 SKU -> Cập nhật đúng và tính lại tổng tiền")
    void updateItemQuantity_shouldUpdateAndRecalculate() {
        when(redisService.hGet(cartKey, "SKU-AO-DEN-L")).thenReturn("1");

        Map<Object, Object> cartData = new HashMap<>();
        cartData.put("SKU-AO-DEN-L", "4");
        when(redisService.hGetAll(cartKey)).thenReturn(cartData);
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-AO-DEN-L"))).thenReturn(List.of(mockAttr1));

        Response<ShoppingCartDto> response = shoppingCartService.updateItemQuantity("SKU-AO-DEN-L", 4);

        verify(redisService).hSet(cartKey, "SKU-AO-DEN-L", "4");
        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(4, dto.getTotalItems());
        assertEquals(480000.0, dto.getFinalAmount()); // 120k * 4
    }

    @Test
    @DisplayName("Cập nhật số lượng về 0 -> Tự động xóa item khỏi giỏ")
    void updateItemQuantity_zero_shouldRemoveItem() {
        when(redisService.hGetAll(cartKey)).thenReturn(Collections.emptyMap());

        Response<ShoppingCartDto> response = shoppingCartService.updateItemQuantity("SKU-AO-DEN-L", 0);

        verify(redisService).hDelete(cartKey, "SKU-AO-DEN-L");
        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(0, dto.getTotalItems());
        assertTrue(dto.getItems().isEmpty());
    }

    @Test
    @DisplayName("Xóa 1 sản phẩm theo SKU -> Xóa thành công")
    void removeItem_shouldRemoveFromCart() {
        when(redisService.hGet(cartKey, "SKU-AO-DEN-L")).thenReturn("1");

        Map<Object, Object> cartData = new HashMap<>();
        cartData.put("SKU-AO-TRANG-M", "2");
        when(redisService.hGetAll(cartKey)).thenReturn(cartData);
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-AO-TRANG-M"))).thenReturn(List.of(mockAttr2));

        Response<ShoppingCartDto> response = shoppingCartService.removeItem("SKU-AO-DEN-L");

        verify(redisService).hDelete(cartKey, "SKU-AO-DEN-L");
        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(1, dto.getItems().size());
        assertEquals("SKU-AO-TRANG-M", dto.getItems().get(0).getSku());
    }

    @Test
    @DisplayName("Xóa toàn bộ giỏ hàng -> Gọi unlink và trả về giỏ hàng trống")
    void clearCart_shouldEmptyCart() {
        Response<ShoppingCartDto> response = shoppingCartService.clearCart();

        verify(redisService).unlink(cartKey);
        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(0, dto.getTotalItems());
        assertTrue(dto.getItems().isEmpty());
    }

    @Test
    @DisplayName("Lấy nhanh số lượng item trong giỏ (Cart Count Badge) -> Trả về tổng số lượng từ Redis")
    void getCartCount_shouldReturnCorrectItemSum() {
        when(redisService.hValues(cartKey)).thenReturn(List.of("3", "2"));

        Response<Integer> response = shoppingCartService.getCartCount();

        assertNotNull(response);
        assertEquals(5, response.getData()); // 3 + 2 = 5
    }

    @Test
    @DisplayName("Thêm sản phẩm hết hàng (UNAVAILABLE) -> Ném BusinessException ATTRIBUTES_OUT_OF_STOCK")
    void addToCart_outOfStockSku_shouldThrowException() {
        Attributes outOfStockAttr = Attributes.builder()
                .sku(new SkuInfo("SKU-HET-HANG"))
                .name("Sản phẩm hết hàng")
                .price(100000.0)
                .statusProduct(StockStatus.UNAVAILABLE)
                .product(mockProduct)
                .build();
        outOfStockAttr.setId(103L);

        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-HET-HANG"))).thenReturn(List.of(outOfStockAttr));

        CartItemRequest request = CartItemRequest.builder()
                .sku("SKU-HET-HANG")
                .quantity(1)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> shoppingCartService.addToCart(List.of(request)));
        assertEquals(ErrorCode.ATTRIBUTES_OUT_OF_STOCK, ex.getErrorCode());
    }

    @Test
    @DisplayName("Thêm số lượng vượt quá 99 -> Ném BusinessException VALIDATION_FAILED")
    void addToCart_exceedMaxQuantity_shouldThrowException() {
        CartItemRequest request = CartItemRequest.builder()
                .sku("SKU-AO-DEN-L")
                .quantity(100)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> shoppingCartService.addToCart(List.of(request)));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Khách vãng lai (Guest) thêm sản phẩm vào giỏ -> Lưu vào key cart:guest:items:{guestId}")
    void addToCart_guestUser_shouldSaveToGuestKey() {
        when(securityUtil.getCurrentUsername()).thenReturn("anonymous");

        String guestId = "guest-uuid-123";
        String guestCartKey = "cart:guest:items:guest-uuid-123";

        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-AO-DEN-L"))).thenReturn(List.of(mockAttr1));

        Map<Object, Object> cartData = new HashMap<>();
        cartData.put("SKU-AO-DEN-L", "1");
        when(redisService.hGetAll(guestCartKey)).thenReturn(cartData);

        CartItemRequest request = CartItemRequest.builder()
                .sku("SKU-AO-DEN-L")
                .quantity(1)
                .build();

        Response<ShoppingCartDto> response = shoppingCartService.addToCart(List.of(request), guestId);

        verify(redisService).hIncrBy(guestCartKey, "SKU-AO-DEN-L", 1);
        assertNotNull(response);
        assertEquals("guest:guest-uuid-123", response.getData().getUsername());
    }

    @Test
    @DisplayName("Hợp nhất giỏ hàng (Merge Cart) -> Gộp số lượng từ Guest Cart vào User Cart và xóa Guest Cart")
    void mergeCart_shouldMergeAndCleanGuestCart() {
        String guestId = "guest-uuid-123";
        String guestCartKey = "cart:guest:items:guest-uuid-123";

        Map<Object, Object> guestData = new HashMap<>();
        guestData.put("SKU-AO-DEN-L", "3");
        guestData.put("SKU-AO-TRANG-M", "2");
        when(redisService.hGetAll(guestCartKey)).thenReturn(guestData);

        Map<Object, Object> userExistingData = new HashMap<>();
        userExistingData.put("SKU-AO-DEN-L", "2"); // User đã có 2 cái SKU-AO-DEN-L -> tổng thành 5
        when(redisService.hGetAll(cartKey)).thenReturn(userExistingData);

        when(attributesRepository.findAllBySku_skuIn(anyList())).thenReturn(List.of(mockAttr1, mockAttr2));

        Response<ShoppingCartDto> response = shoppingCartService.mergeCart(guestId);

        verify(redisService).hSet(cartKey, "SKU-AO-DEN-L", "5");
        verify(redisService).hSet(cartKey, "SKU-AO-TRANG-M", "2");
        verify(redisService).unlink(guestCartKey);
        assertNotNull(response);
        assertEquals(mockUser.getUsername(), response.getData().getUsername());
    }

    @Test
    @DisplayName("Response giỏ hàng phải có trường stock và isAvailable được gán đầy đủ")
    void fetchAndEnrichCart_shouldPopulateStockAndIsAvailable() {
        Map<Object, Object> cartData = new HashMap<>();
        cartData.put("SKU-AO-DEN-L", "1");
        when(redisService.hGetAll(cartKey)).thenReturn(cartData);
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-AO-DEN-L"))).thenReturn(List.of(mockAttr1));

        Response<ShoppingCartDto> response = shoppingCartService.getCart();

        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(1, dto.getItems().size());
        assertTrue(dto.getItems().get(0).getIsAvailable());
        assertEquals(999, dto.getItems().get(0).getStock());
    }

    @Test
    @DisplayName("GraphQL Fast Path: Chỉ query totalItems và username -> Không gọi Database")
    void getCart_fastPath_shouldNotQueryDatabase() {
        Map<Object, Object> cartData = new HashMap<>();
        cartData.put("SKU-AO-DEN-L", "2");
        cartData.put("SKU-AO-TRANG-M", "3");
        when(redisService.hGetAll(cartKey)).thenReturn(cartData);

        Response<ShoppingCartDto> response = shoppingCartService.getCart(null, List.of("totalItems", "username"), null);

        // Đảm bảo tuyệt đối không truy vấn DB
        verify(attributesRepository, never()).findAllBySku_skuIn(any());
        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(5, dto.getTotalItems());
        assertEquals("test@example.com", dto.getUsername());
        assertNull(dto.getTotalPrice());
        assertNull(dto.getItems());
    }

    @Test
    @DisplayName("GraphQL Sparse Fieldset: Chỉ lấy items.sku, items.quantity và finalAmount -> Chỉ serialize trường được chọn")
    void getCart_sparseFields_shouldOnlyPopulateRequestedFields() {
        Map<Object, Object> cartData = new HashMap<>();
        cartData.put("SKU-AO-DEN-L", "2");
        when(redisService.hGetAll(cartKey)).thenReturn(cartData);
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-AO-DEN-L"))).thenReturn(List.of(mockAttr1));

        Response<ShoppingCartDto> response = shoppingCartService.getCart(null, List.of("items.sku", "items.quantity", "finalAmount"), null);

        assertNotNull(response);
        ShoppingCartDto dto = response.getData();
        assertEquals(240000.0, dto.getFinalAmount()); // 120k * 2
        assertNull(dto.getTotalPrice()); // Không yêu cầu -> null
        assertNull(dto.getTotalDiscount()); // Không yêu cầu -> null
        assertNotNull(dto.getItems());
        assertEquals(1, dto.getItems().size());

        var item = dto.getItems().get(0);
        assertEquals("SKU-AO-DEN-L", item.getSku());
        assertEquals(2, item.getQuantity());
        assertNull(item.getUnitPrice()); // Không yêu cầu -> null
        assertNull(item.getProductName()); // Không yêu cầu -> null
        assertNull(item.getImageUrl()); // Không yêu cầu -> null
    }
}
