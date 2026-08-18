package com.ddicg.erp.modules.iam.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AddressResponse {

    String sku;
    String address;
    Double latitude;
    Double longitude;
    String phoneNumber;
    String recipientName;
    Boolean isDefault;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
