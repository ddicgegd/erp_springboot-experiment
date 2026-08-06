package com.ddicg.erp.service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangeUsernameRequest {
    String token;

    @NotBlank(message = "Tên đăng nhập mới không được để trống")
    @Size(min = 3, max = 50, message = "Tên đăng nhập mới phải từ 3 đến 50 ký tự")
    String newUsername;
}
