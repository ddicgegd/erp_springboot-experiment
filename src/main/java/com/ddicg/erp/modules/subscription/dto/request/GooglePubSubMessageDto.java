package com.ddicg.erp.modules.subscription.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GooglePubSubMessageDto {

    PubSubMessage message;
    String subscription;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class PubSubMessage {
        String data;
        String messageId;
        String publishTime;
    }
}
