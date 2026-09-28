package com.ddicg.erp.modules.iam.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.modules.iam.dto.UserDto;
import com.ddicg.erp.modules.iam.dto.request.AccountVerificationRequest;
import com.ddicg.erp.modules.iam.dto.request.ResendVerificationRequest;
import com.ddicg.erp.modules.iam.dto.request.UpdateCredentialsRequest;
import com.ddicg.erp.modules.iam.dto.response.CredentialActiveStatusResponse;
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
        when(userService.validateResetToken(token)).thenReturn(Response.ok("testuser", "Mã token hợp lệ. Vui lòng thiết lập mật khẩu mới."));

        Response<String> response = authController.validateResetToken(token);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("testuser", response.getData());
        verify(userService).validateResetToken(token);
    }

    @Test
    @DisplayName("POST /api/auth/reset-password ủy quyền đúng cho userService.resetPassword")
    void resetPassword_ShouldDelegateToUserService() {
        AccountVerificationRequest request = new AccountVerificationRequest();
        request.setToken("recovery-token-123");
        request.setNewPassword("NewPass123@");
        request.setConfirmPassword("NewPass123@");

        when(userService.resetPassword(request))
                .thenReturn(Response.ok("Mật khẩu đã được thay đổi thành công. Vui lòng đăng nhập lại."));

        Response<String> response = authController.resetPassword(request);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        verify(userService).resetPassword(request);
    }
    @Test
    @DisplayName("POST /api/auth/credential-change/request ủy quyền đúng cho userService.requestCredentialChange")
    void requestCredentialChange_ShouldDelegateToUserService() {
        when(userService.requestCredentialChange())
                .thenReturn(Response.ok("Đã gửi email"));

        Response<String> response = authController.requestCredentialChange();

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        verify(userService).requestCredentialChange();
    }

    @Test
    @DisplayName("GET /api/auth/credential-change/activate ủy quyền đúng cho userService.activateCredentialToken")
    void activateCredentialToken_ShouldDelegateToUserService() {
        when(userService.activateCredentialToken("raw-token-123"))
                .thenReturn(Response.ok("Kích hoạt thành công"));

        Response<String> response = authController.activateCredentialToken("raw-token-123");

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("Kích hoạt thành công", response.getStatus().getMessage());
        verify(userService).activateCredentialToken("raw-token-123");
    }

    @Test
    @DisplayName("GET /api/auth/credential-change/activate ném BusinessException khi token không hợp lệ hoặc đã hết hạn")
    void activateCredentialToken_WhenInvalidOrExpired_ShouldPropagateException() {
        when(userService.activateCredentialToken("invalid-token"))
                .thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                        "Liên kết xác thực không hợp lệ hoặc đã hết hạn."));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authController.activateCredentialToken("invalid-token"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        assertEquals("Liên kết xác thực không hợp lệ hoặc đã hết hạn.", ex.getDetail());
        verify(userService).activateCredentialToken("invalid-token");
    }
    @Test
    @DisplayName("GET /api/auth/credential-change/status ủy quyền đúng cho userService.getCredentialChangeStatus")
    void getCredentialChangeStatus_ShouldDelegateToUserService() {
        CredentialActiveStatusResponse statusResponse = CredentialActiveStatusResponse.builder()
                .status("ACTIVE")
                .remainingSeconds(280L)
                .build();

        when(userService.getCredentialChangeStatus())
                .thenReturn(Response.ok(statusResponse));

        Response<CredentialActiveStatusResponse> response = authController.getCredentialChangeStatus();

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("ACTIVE", response.getData().getStatus());
        verify(userService).getCredentialChangeStatus();
    }

    @Test
    @DisplayName("PUT /api/auth/update-credentials ủy quyền đúng cho userService.updateCredentials")
    void updateCredentials_ShouldDelegateToUserService() {
        UpdateCredentialsRequest request = UpdateCredentialsRequest.builder()
                .newUsername("new_name")
                .build();

        when(userService.updateCredentials(request))
                .thenReturn(Response.ok("Cập nhật thành công"));

        Response<String> response = authController.updateCredentials(request);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        verify(userService).updateCredentials(request);
    }

    @Test
    @DisplayName("GET /api/auth/verify-email?token={token} ủy quyền đúng cho userService.verifyEmail")
    void verifyEmail_ShouldDelegateToUserService() {
        String token = "otp-token-xyz";
        when(userService.verifyEmail(token)).thenReturn(Response.ok("Active"));

        Response<String> response = authController.verifyEmail(token);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        verify(userService).verifyEmail(token);
    }

    @Test
    @DisplayName("POST /api/auth/resend-verification ủy quyền đúng cho userService.resendVerificationEmail")
    void resendVerificationEmail_ShouldDelegateToUserService() {
        ResendVerificationRequest request = new ResendVerificationRequest("inactive@example.com");
        when(userService.resendVerificationEmail(request))
                .thenReturn(Response.ok("Đã gửi lại email xác thực."));

        Response<String> response = authController.resendVerificationEmail(request);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        verify(userService).resendVerificationEmail(request);
    }
}
