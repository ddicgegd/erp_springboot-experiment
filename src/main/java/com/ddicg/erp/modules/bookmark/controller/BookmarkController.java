package com.ddicg.erp.modules.bookmark.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.bookmark.dto.request.AddBookmarkItemRequest;
import com.ddicg.erp.modules.bookmark.dto.request.StageBookmarkRequest;
import com.ddicg.erp.modules.bookmark.dto.response.BookmarkDetailResponse;
import com.ddicg.erp.modules.bookmark.dto.response.BookmarkItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Bookmark", description = "Quản lý phụ kiện mua cùng chuẩn bị lên đơn hàng tại PDP")
@RequestMapping("/api/bookmarks")
public interface BookmarkController {

    @Operation(summary = "Lấy chi tiết phụ kiện đã bookmark của sản phẩm chính")
    @GetMapping("/{mainSku}")
    @ResponseStatus(HttpStatus.OK)
    Response<BookmarkDetailResponse> getBookmark(
            @PathVariable String mainSku,
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId);

    @Operation(summary = "Thủ công (User click +): Thêm/cộng dồn 1 phụ kiện vào Bookmark dài hạn (TTL 7 ngày)")
    @PostMapping("/{mainSku}/items")
    @ResponseStatus(HttpStatus.OK)
    Response<BookmarkItemResponse> addItem(
            @PathVariable String mainSku,
            @Valid @RequestBody AddBookmarkItemRequest request,
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId);

    @Operation(summary = "Tự động (Auto-save ngầm): Đồng bộ danh sách phụ kiện đang cân nhắc (TTL 1 giờ)")
    @PostMapping("/{mainSku}/staging")
    @ResponseStatus(HttpStatus.OK)
    Response<BookmarkDetailResponse> stageItems(
            @PathVariable String mainSku,
            @Valid @RequestBody StageBookmarkRequest request,
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId);

    @Operation(summary = "Gia hạn/Chuyển bookmark từ Staging sang 7 ngày")
    @PostMapping("/{mainSku}/persist")
    @ResponseStatus(HttpStatus.OK)
    Response<BookmarkDetailResponse> persistBookmark(
            @PathVariable String mainSku,
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId);

    @Operation(summary = "Xóa 1 phụ kiện khỏi Bookmark")
    @DeleteMapping("/{mainSku}/items/{sku}")
    @ResponseStatus(HttpStatus.OK)
    Response<Void> removeItem(
            @PathVariable String mainSku,
            @PathVariable String sku,
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId);

    @Operation(summary = "Xóa toàn bộ Bookmark của sản phẩm chính")
    @DeleteMapping("/{mainSku}")
    @ResponseStatus(HttpStatus.OK)
    Response<Void> clearBookmark(
            @PathVariable String mainSku,
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId);

    @Operation(summary = "Lấy tất cả Bookmark của người dùng/guest")
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    Response<java.util.List<BookmarkDetailResponse>> getAllBookmarks(
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId);
}
