package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.core.common.service.MinioService;
import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.core.event.domainevent.AccountRecoveryEvent;
import com.ddicg.erp.core.event.service.ActiveLogService;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.iam.dto.UserDto;
import com.ddicg.erp.modules.iam.dto.request.AccountVerificationRequest;
import com.ddicg.erp.modules.iam.dto.request.ChangeUsernameRequest;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.merchandise.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceRecoveryTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Helper helper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private DeviceInfoService deviceInfoService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ActiveLogService activeLogService;

    @Mock
    private RedisService redisService;

    @Mock
    private JwtService jwtService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SecurityUtil securityUtil;

    @Mock
    private MinioService minioService;

    @Mock
    private CredentialChangeAuthorization credentialChangeAuthorization;

    @Mock
    private AccountRecoveryService accountRecoveryService;

    @InjectMocks
    private UserService userService;

    private User activeUser;
    private User inactiveUser;

    private User lockedUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .name("active_user")
                .email("active@example.com")
                .status(ActiveStatus.ACTIVE)
                .password("encoded_old_pass")
                .build();
        activeUser.setId(10L);

        inactiveUser = User.builder()
                .name("inactive_user")
                .email("inactive@example.com")
                .status(ActiveStatus.INACTIVE)
                .password("encoded_pass")
                .build();
        inactiveUser.setId(20L);

        lockedUser = User.builder()
                .name("locked_user")
                .email("locked@example.com")
                .status(ActiveStatus.LOCKED)
                .password("encoded_locked_pass")
                .build();
        lockedUser.setId(30L);
    }

    @Test
    @DisplayName("recoverAccount: Thành công, thiết lập rate limit và phát hành AccountRecoveryEvent")
    void recoverAccount_ShouldIssueTokenAndPublishEvent() {
        when(helper.isEmailFormat("active@example.com")).thenReturn(true);
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(activeUser));
        RecoveryToken recoveryToken = new RecoveryToken(activeUser, "recovery-tok", "active@example.com");
        when(accountRecoveryService.issue("active@example.com")).thenReturn(recoveryToken);
        when(helper.maskEmail("active@example.com")).thenReturn("a***@example.com");
        when(redisService.increment(RedisTable.AUTH_RECOVERY_QUOTA, "active@example.com")).thenReturn(1L);

        Response<String> response = userService.recoverAccount("active@example.com");

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("Nếu email tồn tại trên hệ thống, liên kết khôi phục tài khoản đã được gửi đến a***@example.com. Vui lòng kiểm tra.", response.getStatus().getMessage());

        // Verify rate limiting keys were set
        verify(redisService).setValueWithExpiry(eq(RedisTable.AUTH_RECOVERY_COOLDOWN), eq("active@example.com"), eq("true"), eq(60L), any());
        verify(redisService).increment(RedisTable.AUTH_RECOVERY_QUOTA, "active@example.com");
        verify(redisService).expire(eq(RedisTable.AUTH_RECOVERY_QUOTA), eq("active@example.com"), eq(3600L), any());

        ArgumentCaptor<AccountRecoveryEvent> eventCaptor = ArgumentCaptor.forClass(AccountRecoveryEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        AccountRecoveryEvent event = eventCaptor.getValue();
        assertEquals(activeUser, event.user());
        assertEquals("recovery-tok", event.token());
    }

    @Test
    @DisplayName("recoverAccount (Anti-Enumeration): Email không tồn tại vẫn trả về 200 OK nhưng không cấp token hay phát event")
    void recoverAccount_WhenEmailDoesNotExist_ShouldReturnGeneric200AndNotPublishEvent() {
        when(helper.isEmailFormat("nonexistent@example.com")).thenReturn(true);
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());
        when(helper.maskEmail("nonexistent@example.com")).thenReturn("n***@example.com");

        Response<String> response = userService.recoverAccount("nonexistent@example.com");

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("Nếu email tồn tại trên hệ thống, liên kết khôi phục tài khoản đã được gửi đến n***@example.com. Vui lòng kiểm tra.", response.getStatus().getMessage());

        verify(accountRecoveryService, never()).issue(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("recoverAccount (Anti-Enumeration): User LOCKED vẫn trả về 200 OK nhưng không cấp token hay phát event")
    void recoverAccount_WhenUserIsLocked_ShouldReturnGeneric200AndNotPublishEvent() {
        when(helper.isEmailFormat("locked@example.com")).thenReturn(true);
        when(userRepository.findByEmail("locked@example.com")).thenReturn(Optional.of(lockedUser));
        when(helper.maskEmail("locked@example.com")).thenReturn("l***@example.com");

        Response<String> response = userService.recoverAccount("locked@example.com");

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("Nếu email tồn tại trên hệ thống, liên kết khôi phục tài khoản đã được gửi đến l***@example.com. Vui lòng kiểm tra.", response.getStatus().getMessage());

        verify(accountRecoveryService, never()).issue(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("recoverAccount: Phải được cấu hình @Transactional(readOnly = true)")
    void recoverAccount_ShouldBeConfiguredWithReadOnlyTransaction() throws NoSuchMethodException {
        var method = UserService.class.getMethod("recoverAccount", String.class);
        var transactional = method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertNotNull(transactional, "Phải có annotation @Transactional");
        assertTrue(transactional.readOnly(), "recoverAccount không ghi database nên transaction phải là readOnly = true");
    }

    @Test
    @DisplayName("recoverAccount (Rate Limit): Ném TOO_MANY_REQUESTS khi đang trong thời gian Cooldown 60s")
    void recoverAccount_WhenCooldownActive_ShouldThrowTooManyRequests() {
        when(helper.isEmailFormat("active@example.com")).thenReturn(true);
        when(redisService.hasKey(RedisTable.AUTH_RECOVERY_COOLDOWN, "active@example.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.recoverAccount("active@example.com"));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("quá nhanh"));
        verify(userRepository, never()).findByEmail(any());
        verify(accountRecoveryService, never()).issue(any());
    }

    @Test
    @DisplayName("recoverAccount (Rate Limit): Ném TOO_MANY_REQUESTS khi vượt quá hạn ngạch 5 lần/giờ")
    void recoverAccount_WhenQuotaExceeded_ShouldThrowTooManyRequests() {
        when(helper.isEmailFormat("active@example.com")).thenReturn(true);
        when(redisService.hasKey(RedisTable.AUTH_RECOVERY_COOLDOWN, "active@example.com")).thenReturn(false);
        when(redisService.increment(RedisTable.AUTH_RECOVERY_QUOTA, "active@example.com")).thenReturn(6L);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.recoverAccount("active@example.com"));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("1 giờ"));
        verify(userRepository, never()).findByEmail(any());
        verify(accountRecoveryService, never()).issue(any());
    }

    @Test
    @DisplayName("recoverAccount: Ném INVALID_FORMAT khi email không đúng định dạng")
    void recoverAccount_WhenInvalidEmailFormat_ShouldThrowInvalidFormat() {
        when(helper.isEmailFormat("bad-email")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.recoverAccount("bad-email"));

        assertEquals(ErrorCode.INVALID_FORMAT, ex.getErrorCode());
        verify(redisService, never()).hasKey(any(), any());
    }

    @Test
    @DisplayName("validateResetToken: User INACTIVE được tự động kích hoạt lên ACTIVE, lưu DB, trả về username và không tiêu thụ token")
    void validateResetToken_WhenInactive_ShouldActivateUserAndReturnUsername() {
        RecoveryToken recoveryToken = new RecoveryToken(inactiveUser, "tok-inactive", "inactive@example.com");
        when(accountRecoveryService.resolve("tok-inactive")).thenReturn(recoveryToken);

        Response<String> response = userService.validateResetToken("tok-inactive");

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("inactive_user", response.getData());
        assertEquals("Tài khoản của bạn đã được kích hoạt thành công. Vui lòng thiết lập mật khẩu mới.", response.getStatus().getMessage());
        assertEquals(ActiveStatus.ACTIVE, inactiveUser.getStatus());
        verify(userRepository).save(inactiveUser);
        verify(accountRecoveryService, never()).consume(any());
    }

    @Test
    @DisplayName("validateResetToken: User ACTIVE trả về username và thông điệp hợp lệ mà không sửa đổi DB")
    void validateResetToken_WhenActive_ShouldReturnUsernameWithoutModifyingDB() {
        RecoveryToken recoveryToken = new RecoveryToken(activeUser, "tok-active", "active@example.com");
        when(accountRecoveryService.resolve("tok-active")).thenReturn(recoveryToken);

        Response<String> response = userService.validateResetToken("tok-active");

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("active_user", response.getData());
        assertEquals("Mã token hợp lệ. Vui lòng thiết lập mật khẩu mới.", response.getStatus().getMessage());
        verify(userRepository, never()).save(any());
        verify(accountRecoveryService, never()).consume(any());
    }

    @Test
    @DisplayName("changeUsername: Ném lỗi ACCESS_DENIED khi cố gắng sử dụng recovery token để đổi username")
    void changeUsername_WhenUsingRecoveryToken_ShouldThrowAccessDenied() {
        ChangeUsernameRequest request = new ChangeUsernameRequest();
        request.setToken("tok-recovery");
        request.setNewUsername("new_name");

        when(credentialChangeAuthorization.resolveFromRecoveryTokenOrSession("tok-recovery"))
                .thenThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "Mã khôi phục không có quyền thay đổi tên đăng nhập."));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.changeUsername(request));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("không có quyền thay đổi"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeUsername với Session đăng nhập: Kiểm tra và áp dụng cooldown 30 ngày")
    void changeUsername_WhenUsingSession_ShouldApplyCooldown() {
        var auth = CredentialChangeAuthorization.Authorization.session(activeUser);

        ChangeUsernameRequest request = new ChangeUsernameRequest();
        request.setToken(null);
        request.setNewUsername("new_active_username");

        when(credentialChangeAuthorization.resolveFromRecoveryTokenOrSession(null)).thenReturn(auth);
        when(redisService.hasKey(RedisTable.AUTH_GUARD_COOLDOWN, 10L)).thenReturn(false);
        when(userRepository.findByName("new_active_username")).thenReturn(Optional.empty());

        Response<String> response = userService.changeUsername(request);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("new_active_username", activeUser.getName());
        verify(userRepository).save(activeUser);
        verify(redisService).setValueWithExpiry(eq(RedisTable.AUTH_GUARD_COOLDOWN), eq(10L), eq("true"), eq(30L), any());
        verify(refreshTokenService).revokeAllUserTokens(10L);
    }

    @Test
    @DisplayName("changeUsername: Ném ngoại lệ khi tên đăng nhập mới đã tồn tại")
    void changeUsername_WhenUsernameExists_ShouldThrowInvalidCredentials() {
        var auth = CredentialChangeAuthorization.Authorization.session(activeUser);

        ChangeUsernameRequest request = new ChangeUsernameRequest();
        request.setToken(null);
        request.setNewUsername("already_taken");

        when(credentialChangeAuthorization.resolveFromRecoveryTokenOrSession(null)).thenReturn(auth);
        when(userRepository.findByName("already_taken")).thenReturn(Optional.of(activeUser));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.changeUsername(request));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("đã tồn tại"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword nhận token qua Request Body: Kích hoạt safeguard nếu user INACTIVE, đổi mật khẩu và thu hồi token")
    void resetPassword_WhenTokenInBodyAndUserInactive_ShouldActivateUserAndUpdatePassword() {
        RecoveryToken recoveryToken = new RecoveryToken(inactiveUser, "tok-body", "inactive@example.com");
        var auth = CredentialChangeAuthorization.Authorization.recovery(recoveryToken);

        when(credentialChangeAuthorization.resolveFromRecoveryToken("tok-body")).thenReturn(auth);
        when(passwordEncoder.encode("newPass123")).thenReturn("encoded_new_pass");

        AccountVerificationRequest request = new AccountVerificationRequest();
        request.setToken("tok-body");
        request.setNewPassword("newPass123");
        request.setConfirmPassword("newPass123");

        Response<String> response = userService.resetPassword(null, request);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("encoded_new_pass", inactiveUser.getPassword());
        assertEquals(ActiveStatus.ACTIVE, inactiveUser.getStatus());
        verify(userRepository).save(inactiveUser);
        verify(credentialChangeAuthorization).consumeRecoveryToken(auth);
        verify(refreshTokenService).revokeAllUserTokens(20L);
    }

    @Test
    @DisplayName("resetPassword với token của user ACTIVE: Đổi mật khẩu thành công và thu hồi token")
    void resetPassword_WhenActiveToken_ShouldSucceed() {
        RecoveryToken recoveryToken = new RecoveryToken(activeUser, "tok-active", "active@example.com");
        var auth = CredentialChangeAuthorization.Authorization.recovery(recoveryToken);

        when(credentialChangeAuthorization.resolveFromRecoveryToken("tok-active")).thenReturn(auth);
        when(passwordEncoder.encode("newPass123")).thenReturn("encoded_new_pass");

        AccountVerificationRequest request = new AccountVerificationRequest();
        request.setNewPassword("newPass123");
        request.setConfirmPassword("newPass123");

        Response<String> response = userService.resetPassword("tok-active", request);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("encoded_new_pass", activeUser.getPassword());
        verify(userRepository).save(activeUser);
        verify(credentialChangeAuthorization).consumeRecoveryToken(auth);
        verify(refreshTokenService).revokeAllUserTokens(10L);
    }
}
