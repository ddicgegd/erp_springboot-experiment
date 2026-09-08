package com.ddicg.erp.modules.bookmark.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.bookmark.dto.request.AddBookmarkItemRequest;
import com.ddicg.erp.modules.bookmark.dto.request.StageBookmarkRequest;
import com.ddicg.erp.modules.bookmark.dto.response.BookmarkDetailResponse;
import com.ddicg.erp.modules.bookmark.dto.response.BookmarkItemResponse;

public interface BookmarkService {

    Response<BookmarkDetailResponse> getBookmark(String mainSku, String guestId);

    Response<BookmarkItemResponse> addItem(String mainSku, AddBookmarkItemRequest request, String guestId);

    Response<BookmarkDetailResponse> stageItems(String mainSku, StageBookmarkRequest request, String guestId);

    Response<BookmarkDetailResponse> persistBookmark(String mainSku, String guestId);

    Response<Void> removeItem(String mainSku, String sku, String guestId);

    Response<Void> clearBookmark(String mainSku, String guestId);

    Response<java.util.List<BookmarkDetailResponse>> getAllBookmarks(String guestId);
}
