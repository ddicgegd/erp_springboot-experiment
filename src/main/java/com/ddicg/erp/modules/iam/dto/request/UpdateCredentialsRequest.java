package com.ddicg.erp.modules.iam.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCredentialsRequest {

    @Size(min = 3, max = 50, message = "Tên đăng nhập mới phải từ 3 đến 50 ký tự.")
    private String newUsername;

    @Size(min = 6, message = "Mật khẩu mới phải có ít nhất 6 ký tự.")
    private String newPassword;

    private String confirmPassword;
}
