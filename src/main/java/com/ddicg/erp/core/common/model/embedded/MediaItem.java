package com.ddicg.erp.core.common.model.embedded;

import jakarta.persistence.Embeddable;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Embeddable
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaItem {
    String key;
    String url;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public static MediaItemBuilder builder() { return new MediaItemBuilder(); }
    public static class MediaItemBuilder {
        private String url;
        private String mediaType;
        private Integer displayOrder;
        MediaItemBuilder() {}
        public MediaItemBuilder url(String url) { this.url = url; return this; }
        public MediaItemBuilder key(String key) { this.url = key; return this; }
        public MediaItemBuilder mediaType(String mediaType) { this.mediaType = mediaType; return this; }
        public MediaItemBuilder displayOrder(Integer displayOrder) { this.displayOrder = displayOrder; return this; }
        public MediaItem build() { MediaItem m = new MediaItem(); m.setUrl(url); return m; }
    }


    public String getKey() { return url; }
}
