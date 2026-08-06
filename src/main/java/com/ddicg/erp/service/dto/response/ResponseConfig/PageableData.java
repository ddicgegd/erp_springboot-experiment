package com.ddicg.erp.service.dto.response.ResponseConfig;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageableData {

    int pageNumber;

    int pageSize;

    int totalPages;

    long totalElements;

    public static PageableData from(Page<?> page) {
        return PageableData.builder()
                .pageNumber(page.getNumber() + 1)
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .build();
    }
}
