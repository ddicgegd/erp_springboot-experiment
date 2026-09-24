package com.ddicg.erp.modules.iam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangeUsernameRequest {
    @NotBlank(message = "Tên đăng nhập mới không được để trống")
    @Size(min = 3, max = 50, message = "Tên đăng nhập mới phải từ 3 đến 50 ký tự.")
    private String newUsername;

    public ChangeUsernameRequest() {}

    public ChangeUsernameRequest(String newUsername) {
        this.newUsername = newUsername;
    }

    public String getNewUsername() { return newUsername; }
    public void setNewUsername(String newUsername) { this.newUsername = newUsername; }
}
