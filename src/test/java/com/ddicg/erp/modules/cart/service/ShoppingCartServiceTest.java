package com.ddicg.erp.modules.cart.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.model.embedded.MediaItem;
import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
import com.ddicg.erp.modules.cart.model.Cart;
import com.ddicg.erp.modules.cart.model.CartItem;
import com.ddicg.erp.modules.cart.repository.CartItemRepository;
import com.ddicg.erp.modules.cart.repository.CartRepository;
import com.ddicg.erp.modules.cart.storage.CartStorageFactory;
import com.ddicg.erp.modules.cart.storage.PersistentDbCartStorage;
import com.ddicg.erp.modules.cart.storage.RedisCartStorage;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.merchandise.model.Product;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    private ShoppingCartServiceImpl shoppingCartService;

    private User mockUser;
    private User mockVipUser;
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
                .rank(UserRank.MEMBER)
                .build();
        mockUser.setId(1L);

        mockVipUser = User.builder()
                .name("vipuser")
                .fullName("VIP User")
                .email("vip@example.com")
                .password("password")
                .rank(UserRank.DIAMOND)
                .build();
        mockVipUser.setId(2L);

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

        RedisCartStorage redisCartStorage = new RedisCartStorage(redisService);
        PersistentDbCartStorage persistentDbCartStorage = new PersistentDbCartStorage(cartRepository, cartItemRepository, redisService);
        CartStorageFactory cartStorageFactory = new CartStorageFactory(redisCartStorage, persistentDbCartStorage);

        shoppingCartService = new ShoppingCartServiceImpl(
                cartStorageFactory,
                redisService,
                attributesRepository,
                userRepository,
                securityUtil
        );

        lenient().when(securityUtil.getCurrentUsername()).thenReturn("testuser");
        lenient().when(userRepository.findByNameOrEmail("testuser")).thenReturn(Optional.of(mockUser));
    }

    @Test
    @DisplayName("Thêm sản phẩm mới vào giỏ hàng (MEMBER thường) -> Lưu Redis và tính đúng tổng tiền")
    void addToCart_standardMember_shouldUseRedis() {
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-AO-DEN-L"))).thenReturn(List.of(mockAttr1));

        Map<Object, Object> cartData = new HashMap<>();
        cartData.put("SKU-AO-DEN-L", "2");
        when(redisService.hGetAll(cartKey)).thenReturn(cartData);

        CartItemRequest request = CartItemRequest.builder()
                .sku("SKU-AO-DEN-L")
                .quantity(2)
                .build();

        Response<ShoppingCartDto> response = shoppingCartService.addToCart(List.of(request), null);

        verify(redisService).hIncrBy(cartKey, "SKU-AO-DEN-L", 2);
        // Không lưu vào DB cho standard member
        verify(cartRepository, never()).save(any());
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
    @DisplayName("Thêm sản phẩm vào giỏ hàng (VIP DIAMOND) -> Lưu vĩnh viễn DB và đồng bộ Redis Cache")
    void addToCart_vipMember_shouldPersistToDatabaseAndCache() {
        String vipCartKey = "cart:items:2";
        when(securityUtil.getCurrentUsername()).thenReturn("vipuser");
        when(userRepository.findByNameOrEmail("vipuser")).thenReturn(Optional.of(mockVipUser));
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-AO-DEN-L"))).thenReturn(List.of(mockAttr1));

        Cart vipCart = Cart.builder().id(50L).user(mockVipUser).items(new ArrayList<>()).build();
        when(cartRepository.findWithItemsByUserId(2L)).thenReturn(Optional.of(vipCart));

        CartItemRequest request = CartItemRequest.builder()
                .sku("SKU-AO-DEN-L")
                .quantity(3)
                .build();

        Response<ShoppingCartDto> response = shoppingCartService.addToCart(List.of(request), null);

        // Verify lưu DB
        verify(cartItemRepository).save(any(CartItem.class));
        // Verify sync cache
        verify(redisService, atLeastOnce()).hSet(eq(vipCartKey), eq("SKU-AO-DEN-L"), eq("3"));

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
    }

    @Test
    @DisplayName("Thêm SKU không tồn tại -> Ném BusinessException")
    void addToCart_invalidSku_shouldThrowException() {
        when(attributesRepository.findAllBySku_skuIn(List.of("INVALID-SKU"))).thenReturn(List.of());

        CartItemRequest request = CartItemRequest.builder()
                .sku("INVALID-SKU")
                .quantity(1)
                .build();

        assertThrows(BusinessException.class, () -> shoppingCartService.addToCart(List.of(request), null));
    }

    @Test
    @DisplayName("Lấy giỏ hàng có chứa SKU đã bị xóa khỏi hệ thống -> Tự động dọn dẹp (Auto-Clean)")
    void getCart_shouldAutoCleanDeletedSkus() {
        Map<Object, Object> cartData = new HashMap<>();
        cartData.put("SKU-AO-DEN-L", "1");
        cartData.put("DEAD-SKU", "1");
        when(redisService.hGetAll(cartKey)).thenReturn(cartData);

        when(attributesRepository.findAllBySku_skuIn(anyList())).thenReturn(List.of(mockAttr1)); // DEAD-SKU không có trong DB

        Response<ShoppingCartDto> response = shoppingCartService.getCart(null, null, null);

        verify(redisService).hDelete(cartKey, "DEAD-SKU");
        assertNotNull(response);
        assertEquals(1, response.getData().getItems().size());
        assertEquals("SKU-AO-DEN-L", response.getData().getItems().get(0).getSku());
    }

    @Test
    @DisplayName("Cập nhật số lượng sản phẩm về 0 -> Xóa khỏi giỏ hàng")
    void updateItemQuantity_zero_shouldRemoveItem() {
        Response<ShoppingCartDto> response = shoppingCartService.updateItemQuantity("SKU-AO-DEN-L", 0, null);

        verify(redisService).hDelete(cartKey, "SKU-AO-DEN-L");
        assertNotNull(response);
    }

    @Test
    @DisplayName("Cập nhật số lượng sản phẩm thành công")
    void updateItemQuantity_valid_shouldUpdate() {
        Map<Object, Object> cartData = new HashMap<>();
        cartData.put("SKU-AO-DEN-L", "1");
        when(redisService.hGetAll(cartKey)).thenReturn(cartData);
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-AO-DEN-L"))).thenReturn(List.of(mockAttr1));

        Response<ShoppingCartDto> response = shoppingCartService.updateItemQuantity("SKU-AO-DEN-L", 5, null);

        verify(redisService).hSet(cartKey, "SKU-AO-DEN-L", "5");
        assertNotNull(response);
    }

    @Test
    @DisplayName("Xóa một sản phẩm khỏi giỏ hàng")
    void removeItem_shouldDeleteFromRedis() {
        Map<Object, Object> initialData = new HashMap<>();
        initialData.put("SKU-AO-DEN-L", "2");
        when(redisService.hGetAll(cartKey)).thenReturn(initialData).thenReturn(Collections.emptyMap());

        Response<ShoppingCartDto> response = shoppingCartService.removeItem("SKU-AO-DEN-L", null);

        verify(redisService, times(1)).hDelete(cartKey, "SKU-AO-DEN-L");
        assertNotNull(response);
    }

    @Test
    @DisplayName("Xóa nhiều sản phẩm khỏi giỏ hàng")
    void removeItems_shouldDeleteBatch() {
        Response<ShoppingCartDto> response = shoppingCartService.removeItems(List.of("SKU-1", "SKU-2"), null);

        verify(redisService).hDelete(cartKey, "SKU-1", "SKU-2");
        assertNotNull(response);
    }

    @Test
    @DisplayName("Xóa toàn bộ giỏ hàng -> Gọi unlink trên Redis")
    void clearCart_shouldUnlinkKey() {
        Response<ShoppingCartDto> response = shoppingCartService.clearCart(null);

        verify(redisService).unlink(cartKey);
        assertNotNull(response);
        assertEquals(0, response.getData().getTotalItems());
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

        Response<ShoppingCartDto> response = shoppingCartService.getCart(null, null, null);

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
}
