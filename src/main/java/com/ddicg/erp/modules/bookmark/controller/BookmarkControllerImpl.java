package com.ddicg.erp.modules.bookmark.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.bookmark.dto.request.AddBookmarkItemRequest;
import com.ddicg.erp.modules.bookmark.dto.request.StageBookmarkRequest;
import com.ddicg.erp.modules.bookmark.dto.response.BookmarkDetailResponse;
import com.ddicg.erp.modules.bookmark.dto.response.BookmarkItemResponse;
import com.ddicg.erp.modules.bookmark.service.BookmarkService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BookmarkControllerImpl implements BookmarkController {

    BookmarkService bookmarkService;

    @Override
    public Response<BookmarkDetailResponse> getBookmark(String mainSku, String guestId) {
        return bookmarkService.getBookmark(mainSku, guestId);
    }

    @Override
    public Response<BookmarkItemResponse> addItem(String mainSku, AddBookmarkItemRequest request, String guestId) {
        return bookmarkService.addItem(mainSku, request, guestId);
    }

    @Override
    public Response<BookmarkDetailResponse> stageItems(String mainSku, StageBookmarkRequest request, String guestId) {
        return bookmarkService.stageItems(mainSku, request, guestId);
    }

    @Override
    public Response<BookmarkDetailResponse> persistBookmark(String mainSku, String guestId) {
        return bookmarkService.persistBookmark(mainSku, guestId);
    }

    @Override
    public Response<Void> removeItem(String mainSku, String sku, String guestId) {
        return bookmarkService.removeItem(mainSku, sku, guestId);
    }

    @Override
    public Response<Void> clearBookmark(String mainSku, String guestId) {
        return bookmarkService.clearBookmark(mainSku, guestId);
    }

    @Override
    public Response<java.util.List<BookmarkDetailResponse>> getAllBookmarks(String guestId) {
        return bookmarkService.getAllBookmarks(guestId);
    }
}
