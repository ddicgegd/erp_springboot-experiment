package com.ddicg.erp.modules.bookmark.model;

import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookmarkContext {
    String ownerId;
    String mainSku;
    boolean isGuest;

    public String getSavedKey() {
        return RedisTable.BOOKMARK_SAVED.key(ownerId + ":" + mainSku);
    }

    public String getStagingKey() {
        return RedisTable.BOOKMARK_STAGING.key(ownerId + ":" + mainSku);
    }
}
