package com.ddicg.erp.core.common.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResolvedAddress {
    boolean success;
    Double latitude;
    Double longitude;
    String formattedAddress;
    String rawAddress;
    String error;
}
