package com.ddicg.erp.modules.iam.dto.request;

import com.ddicg.erp.core.common.model.embedded.DeviceInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginRequest {
    @NotBlank(message = "Tên đăng nhập hoặc email không được để trống")
    @Size(min = 3, max = 50, message = "Tên đăng nhập hoặc email phải từ 3 đến 50 ký tự.")
    private String usernameOrEmail;

    @NotBlank(message = "Mật khẩu không được để trống.")
    private String password;

    @NotNull(message = "Thông tin thiết bị không được để trống.")
    @Valid
    private DeviceInfo deviceInfo;
}
