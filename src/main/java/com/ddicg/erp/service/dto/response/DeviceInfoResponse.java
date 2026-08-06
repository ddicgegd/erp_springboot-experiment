package com.ddicg.erp.service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceInfoResponse {
    String finalRefreshTokenString;
    String accessToken;
    String message;
}
