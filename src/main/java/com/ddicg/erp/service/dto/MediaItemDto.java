package com.ddicg.erp.service.dto;

import jakarta.validation.constraints.Size;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link com.ddicg.erp.model.embedded.MediaItem}
 */
@Value
public class MediaItemDto implements Serializable {
    @Size(max = 5)
    String key;
    String url;
}