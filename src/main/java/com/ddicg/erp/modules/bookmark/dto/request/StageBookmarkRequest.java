package com.ddicg.erp.modules.bookmark.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StageBookmarkRequest {

    @NotEmpty(message = "Danh sách sản phẩm không được rỗng")
    @Valid
    List<AddBookmarkItemRequest> items;
}
