package com.ddicg.erp.modules.bookmark.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.bookmark.dto.request.AddBookmarkItemRequest;
import com.ddicg.erp.modules.bookmark.dto.request.StageBookmarkRequest;
import com.ddicg.erp.modules.bookmark.dto.response.BookmarkDetailResponse;
import com.ddicg.erp.modules.bookmark.dto.response.BookmarkItemResponse;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.core.common.model.embedded.SkuInfo;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    private RedisService redisService;

    @Mock
    private AttributesRepository attributesRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityUtil securityUtil;

    @InjectMocks
    private BookmarkServiceImpl bookmarkService;

    private static final String MAIN_SKU = "OPPO-FIND-X8-PRO-BLK";
    private static final String GUEST_ID = "guest-uuid-123";
    private static final String SUB_SKU = "STRAP-APW-01";

    private Attributes testAttr;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .id(100L)
                .name("Dây đeo Apple Watch")
                .build();

        SkuInfo skuObj = SkuInfo.builder().sku(SUB_SKU).build();

        testAttr = Attributes.builder()
                .id(1L)
                .sku(skuObj)
                .name("Size 45mm")
                .product(testProduct)
                .price(570000.0)
                .salePrice(513000.0)
                .statusProduct(StockStatus.AVAILABLE)
                .build();

        lenient().when(attributesRepository.existsBySku_sku(MAIN_SKU)).thenReturn(true);
    }

    @Test
    @DisplayName("Thêm phụ kiện khi bấm (+) thành công lần đầu: ghi Redis atomic increment và set TTL 7 ngày")
    void addItem_Success() {
        when(securityUtil.getCurrentUsername()).thenReturn(null);
        when(attributesRepository.findAttributesBySku_sku(SUB_SKU)).thenReturn(Optional.of(testAttr));
        when(redisService.getExpireSeconds(anyString())).thenReturn(-2L);
        when(redisService.hIncrBy(anyString(), eq(SUB_SKU), eq(1L))).thenReturn(1L);

        AddBookmarkItemRequest req = AddBookmarkItemRequest.builder()
                .sku(SUB_SKU)
                .quantity(1)
                .build();

        Response<BookmarkItemResponse> res = bookmarkService.addItem(MAIN_SKU, req, GUEST_ID);

        assertThat(res).isNotNull();
        assertThat(res.getData().getSku()).isEqualTo(SUB_SKU);
        assertThat(res.getData().getQuantity()).isEqualTo(1);
        assertThat(res.getData().getSalePrice()).isEqualTo(513000.0);
        assertThat(res.getData().getUnitPrice()).isEqualTo(570000.0);
        assertThat(res.getData().getSubTotal()).isEqualTo(513000.0);
        assertThat(res.getData().getIsAvailable()).isTrue();

        verify(redisService).expire(contains("bookmark:saved:guest:" + GUEST_ID + ":" + MAIN_SKU), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    @DisplayName("Thêm phụ kiện khi bookmark đã tồn tại: KHÔNG reset TTL (giữ nguyên thời hạn hết hạn ban đầu)")
    void addItem_Success_DoNotResetTtl() {
        when(securityUtil.getCurrentUsername()).thenReturn(null);
        when(attributesRepository.findAttributesBySku_sku(SUB_SKU)).thenReturn(Optional.of(testAttr));
        when(redisService.getExpireSeconds(anyString())).thenReturn(450000L);
        when(redisService.hIncrBy(anyString(), eq(SUB_SKU), eq(2L))).thenReturn(3L);

        AddBookmarkItemRequest req = AddBookmarkItemRequest.builder()
                .sku(SUB_SKU)
                .quantity(2)
                .build();

        Response<BookmarkItemResponse> res = bookmarkService.addItem(MAIN_SKU, req, GUEST_ID);

        assertThat(res).isNotNull();
        assertThat(res.getData().getQuantity()).isEqualTo(3);

        // Đảm bảo tuyệt đối KHÔNG gọi expire để reset thời gian khi bookmark đã có sẵn
        verify(redisService, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("Thêm phụ kiện thất bại khi phụ kiện không tồn tại trong DB")
    void addItem_NotFound() {
        when(securityUtil.getCurrentUsername()).thenReturn(null);
        when(attributesRepository.findAttributesBySku_sku(SUB_SKU)).thenReturn(Optional.empty());

        AddBookmarkItemRequest req = AddBookmarkItemRequest.builder()
                .sku(SUB_SKU)
                .quantity(1)
                .build();

        assertThatThrownBy(() -> bookmarkService.addItem(MAIN_SKU, req, GUEST_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTRIBUTES_NOT_FOUND);

        verify(redisService, never()).hIncrBy(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Thêm phụ kiện thất bại khi phụ kiện hết hàng (UNAVAILABLE)")
    void addItem_OutOfStock() {
        testAttr.setStatusProduct(StockStatus.UNAVAILABLE);
        when(securityUtil.getCurrentUsername()).thenReturn(null);
        when(attributesRepository.findAttributesBySku_sku(SUB_SKU)).thenReturn(Optional.of(testAttr));

        AddBookmarkItemRequest req = AddBookmarkItemRequest.builder()
                .sku(SUB_SKU)
                .quantity(1)
                .build();

        assertThatThrownBy(() -> bookmarkService.addItem(MAIN_SKU, req, GUEST_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTRIBUTES_OUT_OF_STOCK);

        verify(redisService, never()).hIncrBy(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Tự động lưu danh sách chuẩn bị (Staging): unlink key cũ, ghi items, bảo toàn TTL còn lại")
    void stageItems_Success() {
        when(securityUtil.getCurrentUsername()).thenReturn(null);
        when(attributesRepository.findAllBySku_skuIn(anyList())).thenReturn(List.of(testAttr));
        when(redisService.getExpireSeconds(anyString())).thenReturn(2400L);

        Map<Object, Object> raw = new HashMap<>();
        raw.put(SUB_SKU, "2");
        when(redisService.hGetAll(anyString())).thenReturn(raw);

        StageBookmarkRequest req = StageBookmarkRequest.builder()
                .items(List.of(AddBookmarkItemRequest.builder().sku(SUB_SKU).quantity(2).build()))
                .build();

        Response<BookmarkDetailResponse> res = bookmarkService.stageItems(MAIN_SKU, req, GUEST_ID);

        assertThat(res.getData().getMainSku()).isEqualTo(MAIN_SKU);
        assertThat(res.getData().getTotalItems()).isEqualTo(2);
        assertThat(res.getData().getTotalSalePrice()).isEqualTo(1026000.0);
        assertThat(res.getData().getTtlSecondsRemaining()).isEqualTo(2400L);
        assertThat(res.getData().getFormattedRemainingTime()).isEqualTo("40 phút");

        String expectedKey = "bookmark:staging:guest:" + GUEST_ID + ":" + MAIN_SKU;
        verify(redisService).unlink(expectedKey);
        verify(redisService).hSet(expectedKey, SUB_SKU, "2");
        verify(redisService).expire(expectedKey, 2400L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Get Bookmark khi chưa có item: trả về empty object")
    void getBookmark_Empty() {
        when(securityUtil.getCurrentUsername()).thenReturn(null);
        when(redisService.hGetAll(anyString())).thenReturn(Collections.emptyMap());

        Response<BookmarkDetailResponse> res = bookmarkService.getBookmark(MAIN_SKU, GUEST_ID);

        assertThat(res.getData().getMainSku()).isEqualTo(MAIN_SKU);
        assertThat(res.getData().getTotalItems()).isEqualTo(0);
        assertThat(res.getData().getItems()).isEmpty();
        assertThat(res.getData().getTtlSecondsRemaining()).isEqualTo(0L);
        assertThat(res.getData().getFormattedRemainingTime()).isEqualTo("Đã hết hạn");
    }

    @Test
    @DisplayName("Get Bookmark thành công: tính đúng tổng tiền, chiết khấu, thời gian còn lại và tự dọn dẹp SKU không còn tồn tại")
    void getBookmark_Success_WithAutoClean() {
        when(securityUtil.getCurrentUsername()).thenReturn(null);

        Map<Object, Object> raw = new HashMap<>();
        raw.put(SUB_SKU, "1");
        raw.put("DEAD-SKU", "1");
        when(redisService.hGetAll(anyString())).thenReturn(raw);
        when(redisService.getExpireSeconds(anyString())).thenReturn(500000L);

        // Chỉ tìm thấy SUB_SKU trong DB, DEAD-SKU không có
        when(attributesRepository.findAllBySku_skuIn(anyList())).thenReturn(List.of(testAttr));

        Response<BookmarkDetailResponse> res = bookmarkService.getBookmark(MAIN_SKU, GUEST_ID);

        assertThat(res.getData().getTotalItems()).isEqualTo(1);
        assertThat(res.getData().getItems()).hasSize(1);
        assertThat(res.getData().getItems().get(0).getSku()).isEqualTo(SUB_SKU);
        assertThat(res.getData().getTtlSecondsRemaining()).isEqualTo(500000L);
        assertThat(res.getData().getExpiresAtEpochMs()).isNotNull().isGreaterThan(System.currentTimeMillis());
        assertThat(res.getData().getFormattedRemainingTime()).isNotBlank();

        // Kiểm tra auto-clean đã xóa DEAD-SKU khỏi Redis
        verify(redisService).hDelete(anyString(), eq("DEAD-SKU"));
    }

    @Test
    @DisplayName("Xóa 1 phụ kiện khỏi bookmark")
    void removeItem_Success() {
        when(securityUtil.getCurrentUsername()).thenReturn(null);

        Response<Void> res = bookmarkService.removeItem(MAIN_SKU, SUB_SKU, GUEST_ID);

        assertThat(res).isNotNull();
        verify(redisService).hDelete(contains("bookmark:saved:guest:" + GUEST_ID + ":" + MAIN_SKU), eq(SUB_SKU));
        verify(redisService).hDelete(contains("bookmark:staging:guest:" + GUEST_ID + ":" + MAIN_SKU), eq(SUB_SKU));
    }

    @Test
    @DisplayName("Xóa toàn bộ bookmark của sản phẩm chính")
    void clearBookmark_Success() {
        when(securityUtil.getCurrentUsername()).thenReturn(null);

        Response<Void> res = bookmarkService.clearBookmark(MAIN_SKU, GUEST_ID);

        assertThat(res).isNotNull();
        verify(redisService).unlink(contains("bookmark:saved:guest:" + GUEST_ID + ":" + MAIN_SKU));
        verify(redisService).unlink(contains("bookmark:staging:guest:" + GUEST_ID + ":" + MAIN_SKU));
    }

    @Test
    @DisplayName("Thao tác với User đã đăng nhập: lưu theo ownerId là userId")
    void userAuthenticated_Success() {
        User user = User.builder()
                .id(99L)
                .name("john_doe")
                .build();
        when(securityUtil.getCurrentUsername()).thenReturn("john_doe");
        when(userRepository.findByNameOrEmail("john_doe")).thenReturn(Optional.of(user));
        when(attributesRepository.findAttributesBySku_sku(SUB_SKU)).thenReturn(Optional.of(testAttr));
        when(redisService.getExpireSeconds(anyString())).thenReturn(-2L);
        when(redisService.hIncrBy(anyString(), eq(SUB_SKU), eq(1L))).thenReturn(1L);

        AddBookmarkItemRequest req = AddBookmarkItemRequest.builder()
                .sku(SUB_SKU)
                .quantity(1)
                .build();

        Response<BookmarkItemResponse> res = bookmarkService.addItem(MAIN_SKU, req, null);

        assertThat(res).isNotNull();
        verify(redisService).expire(contains("bookmark:saved:99:" + MAIN_SKU), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    @DisplayName("Thêm phụ kiện thất bại khi mainSku không tồn tại trong DB")
    void addItem_MainSkuNotFound() {
        when(attributesRepository.existsBySku_sku("NON-EXISTENT-MAIN")).thenReturn(false);

        AddBookmarkItemRequest req = AddBookmarkItemRequest.builder()
                .sku(SUB_SKU)
                .quantity(1)
                .build();

        assertThatThrownBy(() -> bookmarkService.addItem("NON-EXISTENT-MAIN", req, GUEST_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTRIBUTES_NOT_FOUND);

        verify(redisService, never()).hIncrBy(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Get Bookmark thất bại khi mainSku không tồn tại trong DB")
    void getBookmark_MainSkuNotFound() {
        when(attributesRepository.existsBySku_sku("NON-EXISTENT-MAIN")).thenReturn(false);

        assertThatThrownBy(() -> bookmarkService.getBookmark("NON-EXISTENT-MAIN", GUEST_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTRIBUTES_NOT_FOUND);

        verify(redisService, never()).hGetAll(anyString());
    }

    @Test
    @DisplayName("Stage Bookmark thất bại khi mainSku không tồn tại trong DB")
    void stageItems_MainSkuNotFound() {
        when(attributesRepository.existsBySku_sku("NON-EXISTENT-MAIN")).thenReturn(false);

        StageBookmarkRequest req = StageBookmarkRequest.builder()
                .items(List.of(AddBookmarkItemRequest.builder().sku(SUB_SKU).quantity(1).build()))
                .build();

        assertThatThrownBy(() -> bookmarkService.stageItems("NON-EXISTENT-MAIN", req, GUEST_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTRIBUTES_NOT_FOUND);

        verify(redisService, never()).unlink(anyString());
    }
}
