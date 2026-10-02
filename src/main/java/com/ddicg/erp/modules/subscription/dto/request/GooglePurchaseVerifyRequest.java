package com.ddicg.erp.modules.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GooglePurchaseVerifyRequest {

    @NotBlank(message = "purchaseToken không được để trống")
    String purchaseToken;

    @NotBlank(message = "planCode không được để trống")
    String planCode;

    String orderId;
}
