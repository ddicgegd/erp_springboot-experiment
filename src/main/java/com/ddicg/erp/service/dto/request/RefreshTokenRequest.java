package com.ddicg.erp.service.dto.request;

import com.ddicg.erp.model.embedded.DeviceInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token không được để trống")
    String refreshToken;

    @NotNull(message = "Thông tin thiết bị không được để trống")
    @Valid
    DeviceInfo deviceInfo;
}
