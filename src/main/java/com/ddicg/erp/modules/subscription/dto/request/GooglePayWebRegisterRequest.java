package com.ddicg.erp.modules.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GooglePayWebRegisterRequest {

    @NotBlank(message = "Mã gói dịch vụ không được để trống")
    String planCode;

    @NotBlank(message = "Google Pay token không được để trống")
    String paymentToken;

    String cardNetwork;

    String lastFour;
}
