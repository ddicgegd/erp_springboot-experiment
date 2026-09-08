package com.ddicg.erp.modules.bookmark.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.bookmark.dto.request.AddBookmarkItemRequest;
import com.ddicg.erp.modules.bookmark.dto.request.StageBookmarkRequest;
import com.ddicg.erp.modules.bookmark.dto.response.BookmarkDetailResponse;
import com.ddicg.erp.modules.bookmark.dto.response.BookmarkItemResponse;
import com.ddicg.erp.modules.bookmark.service.BookmarkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookmarkControllerTest {

    @Mock
    private BookmarkService bookmarkService;

    @InjectMocks
    private BookmarkControllerImpl bookmarkController;

    private static final String MAIN_SKU = "OPPO-FIND-X8-PRO-BLK";
    private static final String GUEST_ID = "guest-uuid-123";

    @Test
    @DisplayName("GET /{mainSku} ủy quyền đúng cho bookmarkService.getBookmark")
    void getBookmark_delegatesToService() {
        BookmarkDetailResponse mockDetail = BookmarkDetailResponse.builder()
                .mainSku(MAIN_SKU)
                .totalItems(2)
                .build();
        when(bookmarkService.getBookmark(MAIN_SKU, GUEST_ID)).thenReturn(Response.ok(mockDetail));

        Response<BookmarkDetailResponse> res = bookmarkController.getBookmark(MAIN_SKU, GUEST_ID);

        assertThat(res).isNotNull();
        assertThat(res.getData().getMainSku()).isEqualTo(MAIN_SKU);
        assertThat(res.getData().getTotalItems()).isEqualTo(2);
        verify(bookmarkService).getBookmark(MAIN_SKU, GUEST_ID);
    }

    @Test
    @DisplayName("POST /{mainSku}/items ủy quyền đúng cho bookmarkService.addItem")
    void addItem_delegatesToService() {
        AddBookmarkItemRequest req = AddBookmarkItemRequest.builder()
                .sku("STRAP-APW-01")
                .quantity(1)
                .build();
        BookmarkItemResponse mockItem = BookmarkItemResponse.builder()
                .sku("STRAP-APW-01")
                .quantity(1)
                .build();
        when(bookmarkService.addItem(MAIN_SKU, req, GUEST_ID)).thenReturn(Response.ok(mockItem));

        Response<BookmarkItemResponse> res = bookmarkController.addItem(MAIN_SKU, req, GUEST_ID);

        assertThat(res).isNotNull();
        assertThat(res.getData().getSku()).isEqualTo("STRAP-APW-01");
        verify(bookmarkService).addItem(MAIN_SKU, req, GUEST_ID);
    }

    @Test
    @DisplayName("POST /{mainSku}/staging ủy quyền đúng cho bookmarkService.stageItems")
    void stageItems_delegatesToService() {
        StageBookmarkRequest req = StageBookmarkRequest.builder()
                .items(List.of(AddBookmarkItemRequest.builder().sku("STRAP-APW-01").quantity(1).build()))
                .build();
        BookmarkDetailResponse mockDetail = BookmarkDetailResponse.builder()
                .mainSku(MAIN_SKU)
                .totalItems(1)
                .build();
        when(bookmarkService.stageItems(MAIN_SKU, req, GUEST_ID)).thenReturn(Response.ok(mockDetail));

        Response<BookmarkDetailResponse> res = bookmarkController.stageItems(MAIN_SKU, req, GUEST_ID);

        assertThat(res).isNotNull();
        verify(bookmarkService).stageItems(MAIN_SKU, req, GUEST_ID);
    }

    @Test
    @DisplayName("DELETE /{mainSku}/items/{sku} ủy quyền đúng cho bookmarkService.removeItem")
    void removeItem_delegatesToService() {
        when(bookmarkService.removeItem(MAIN_SKU, "STRAP-APW-01", GUEST_ID)).thenReturn(Response.ok(null));

        Response<Void> res = bookmarkController.removeItem(MAIN_SKU, "STRAP-APW-01", GUEST_ID);

        assertThat(res).isNotNull();
        verify(bookmarkService).removeItem(MAIN_SKU, "STRAP-APW-01", GUEST_ID);
    }

    @Test
    @DisplayName("DELETE /{mainSku} ủy quyền đúng cho bookmarkService.clearBookmark")
    void clearBookmark_delegatesToService() {
        when(bookmarkService.clearBookmark(MAIN_SKU, GUEST_ID)).thenReturn(Response.ok(null));

        Response<Void> res = bookmarkController.clearBookmark(MAIN_SKU, GUEST_ID);

        assertThat(res).isNotNull();
        verify(bookmarkService).clearBookmark(MAIN_SKU, GUEST_ID);
    }
}
