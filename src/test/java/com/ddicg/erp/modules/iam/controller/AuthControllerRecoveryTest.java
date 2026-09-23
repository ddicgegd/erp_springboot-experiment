package com.ddicg.erp.modules.iam.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.modules.iam.dto.UserDto;
import com.ddicg.erp.modules.iam.dto.request.AccountVerificationRequest;
import com.ddicg.erp.modules.iam.dto.request.ChangeUsernameRequest;
import com.ddicg.erp.modules.iam.service.iUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerRecoveryTest {

    @Mock
    private iUser userService;

    @InjectMocks
    private AuthControllerImpl authController;

    @Test
    @DisplayName("GET /api/auth/recover-account/{email} ủy quyền đúng cho userService.recoverAccount")
    void recoverAccount_ShouldDelegateToUserService() {
        String email = "test@example.com";
        when(userService.recoverAccount(email))
                .thenReturn(Response.ok("Đường dẫn khôi phục tài khoản đã được gửi đến t***@example.com. Vui lòng kiểm tra."));

        Response<String> response = authController.recoverAccount(email);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertTrue(response.getStatus().getMessage().contains("t***@example.com"));
        verify(userService).recoverAccount(email);
    }

    @Test
    @DisplayName("GET /api/auth/validate-reset-token?token={token} ủy quyền đúng cho userService.validateResetToken")
    void validateResetToken_ShouldDelegateToUserService() {
        String token = "uuid-token-xyz";
        UserDto userDto = UserDto.builder()
                .username("testuser")
                .email("test@example.com")
                .active(ActiveStatus.ACTIVE)
                .build();
        when(userService.validateResetToken(token)).thenReturn(Response.ok(userDto));

        Response<UserDto> response = authController.validateResetToken(token);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("testuser", response.getData().getUsername());
        verify(userService).validateResetToken(token);
    }

    @Test
    @DisplayName("POST /api/auth/reset-password?code={code} ủy quyền đúng cho userService.resetPassword")
    void resetPassword_ShouldDelegateToUserService() {
        String code = "recovery-code-123";
        AccountVerificationRequest request = new AccountVerificationRequest();
        request.setNewPassword("NewPass123@");
        request.setConfirmPassword("NewPass123@");

        when(userService.resetPassword(code, request))
                .thenReturn(Response.ok("Mật khẩu đã được thay đổi thành công. Vui lòng đăng nhập lại."));

        Response<String> response = authController.resetPassword(code, request);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        verify(userService).resetPassword(code, request);
    }

    @Test
    @DisplayName("PUT /api/auth/change-username ủy quyền đúng cho userService.changeUsername")
    void changeUsername_ShouldDelegateToUserService() {
        ChangeUsernameRequest request = new ChangeUsernameRequest();
        request.setToken("recovery-token-456");
        request.setNewUsername("brand_new_name");

        when(userService.changeUsername(request))
                .thenReturn(Response.ok("Đổi tên đăng nhập thành công. Vui lòng đăng nhập lại."));

        Response<String> response = authController.changeUsername(request);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        verify(userService).changeUsername(request);
    }
}
