package com.ddicg.erp.modules.merchandise.dto;

import jakarta.validation.constraints.Size;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link com.ddicg.erp.core.common.model.embedded.MediaItem}
 */
@Value
public class MediaItemDto implements Serializable {
    @Size(max = 5)
    String key;
    String url;
}