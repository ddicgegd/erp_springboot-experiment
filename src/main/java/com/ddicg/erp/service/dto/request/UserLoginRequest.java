package com.ddicg.erp.service.dto.request;

import com.ddicg.erp.model.embedded.DeviceInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
public class UserLoginRequest {
    @NotBlank(message = "Tên đăng nhập hoặc email không được để trống")
    @Size(min = 3, max = 50, message = "Tên đăng nhập hoặc email phải từ 3 đến 50 ký tự.")
    String usernameOrEmail;

    @NotBlank(message = "Mật khẩu không được để trống.")
    String password;

    @NotNull(message = "Thông tin thiết bị không được để trống.")
    @Valid
    DeviceInfo deviceInfo;
}