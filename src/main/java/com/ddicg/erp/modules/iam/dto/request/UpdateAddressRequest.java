package com.ddicg.erp.modules.iam.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateAddressRequest {

    @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự")
    String address;

    Double latitude;

    Double longitude;

    @Size(max = 20, message = "Số điện thoại không hợp lệ")
    String phoneNumber;

    @Size(max = 200, message = "Tên người nhận không được vượt quá 200 ký tự")
    String recipientName;

    Boolean isDefault;
}
