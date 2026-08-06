package com.ddicg.erp.service.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CategorySearchRequest {
    List<String> ids;
    List<String> skus;
    List<String> names;

    String keyword;
    String createdBy;
    LocalDateTime createdFrom;
    LocalDateTime createdTo;
    LocalDateTime updatedFrom;
    LocalDateTime updatedTo;

    PagingRequest paging = new PagingRequest();
}
