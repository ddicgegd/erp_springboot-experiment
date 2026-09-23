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

        Response<String> response = userService.recoverAccount("active@example.com");

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertTrue(response.getStatus().getMessage().contains("a***@example.com"));

        // Verify rate limiting keys were set
        verify(redisService).setValueWithExpiry(eq(RedisTable.AUTH_RECOVERY_COOLDOWN), eq("active@example.com"), eq("true"), eq(60L), any());
        verify(redisService).setValueWithExpiry(eq(RedisTable.AUTH_RECOVERY_QUOTA), eq("active@example.com"), eq("1"), anyLong(), any());

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
        assertTrue(response.getStatus().getMessage().contains("n***@example.com"));

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
        assertTrue(response.getStatus().getMessage().contains("l***@example.com"));

        verify(accountRecoveryService, never()).issue(any());
        verify(eventPublisher, never()).publishEvent(any());
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
        when(redisService.getValue(RedisTable.AUTH_RECOVERY_QUOTA, "active@example.com")).thenReturn(5);

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
    @DisplayName("validateResetToken: Trả về UserDto có trạng thái của user và id bị ẩn")
    void validateResetToken_ShouldReturnUserDtoWithHiddenId() {
        RecoveryToken recoveryToken = new RecoveryToken(inactiveUser, "tok-inactive", "inactive@example.com");
        when(accountRecoveryService.resolve("tok-inactive")).thenReturn(recoveryToken);

        UserDto mappedDto = UserDto.builder()
                .username("inactive_user")
                .email("inactive@example.com")
                .active(ActiveStatus.INACTIVE)
                .build();
        when(userMapper.toDto(inactiveUser)).thenReturn(mappedDto);

        Response<UserDto> response = userService.validateResetToken("tok-inactive");

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        UserDto resultDto = response.getData();
        assertEquals("inactive_user", resultDto.getUsername());
        assertEquals(ActiveStatus.INACTIVE, resultDto.getActive());
        assertNull(resultDto.getId());
    }

    @Test
    @DisplayName("changeUsername với tài khoản INACTIVE: Đổi username thành công, tự động kích hoạt tài khoản lên ACTIVE, không đặt cooldown 30 ngày")
    void changeUsername_WhenInactiveAccount_ShouldChangeNameAndActivate() {
        RecoveryToken recoveryToken = new RecoveryToken(inactiveUser, "tok-inactive", "inactive@example.com");
        var auth = CredentialChangeAuthorization.Authorization.recovery(recoveryToken);

        ChangeUsernameRequest request = new ChangeUsernameRequest();
        request.setToken("tok-inactive");
        request.setNewUsername("brand_new_username");

        when(credentialChangeAuthorization.resolveFromRecoveryTokenOrSession("tok-inactive")).thenReturn(auth);
        when(userRepository.findByName("brand_new_username")).thenReturn(Optional.empty());

        Response<String> response = userService.changeUsername(request);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertTrue(response.getStatus().getMessage().contains("kích hoạt tài khoản thành công"));

        assertEquals("brand_new_username", inactiveUser.getName());
        assertEquals(ActiveStatus.ACTIVE, inactiveUser.getStatus());
        verify(userRepository).save(inactiveUser);

        // Đảm bảo không đặt cooldown 30 ngày cho tài khoản mới kích hoạt
        verify(redisService, never()).setValueWithExpiry(eq(RedisTable.AUTH_GUARD_COOLDOWN), any(), any(), anyLong(), any());
        verify(credentialChangeAuthorization).consumeRecoveryToken(auth);
        verify(refreshTokenService).revokeAllUserTokens(20L);
    }

    @Test
    @DisplayName("changeUsername với tài khoản ACTIVE: Kiểm tra và áp dụng cooldown 30 ngày, giữ nguyên status ACTIVE")
    void changeUsername_WhenActiveAccount_ShouldApplyCooldown() {
        RecoveryToken recoveryToken = new RecoveryToken(activeUser, "tok-active", "active@example.com");
        var auth = CredentialChangeAuthorization.Authorization.recovery(recoveryToken);

        ChangeUsernameRequest request = new ChangeUsernameRequest();
        request.setToken("tok-active");
        request.setNewUsername("new_active_username");

        when(credentialChangeAuthorization.resolveFromRecoveryTokenOrSession("tok-active")).thenReturn(auth);
        when(redisService.hasKey(RedisTable.AUTH_GUARD_COOLDOWN, 10L)).thenReturn(false);
        when(userRepository.findByName("new_active_username")).thenReturn(Optional.empty());

        Response<String> response = userService.changeUsername(request);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("new_active_username", activeUser.getName());
        assertEquals(ActiveStatus.ACTIVE, activeUser.getStatus());
        verify(userRepository).save(activeUser);

        // Đã active thì áp dụng cooldown 30 ngày
        verify(redisService).setValueWithExpiry(eq(RedisTable.AUTH_GUARD_COOLDOWN), eq(10L), eq("true"), eq(30L), any());
        verify(credentialChangeAuthorization).consumeRecoveryToken(auth);
        verify(refreshTokenService).revokeAllUserTokens(10L);
    }

    @Test
    @DisplayName("changeUsername: Ném ngoại lệ khi tên đăng nhập mới đã tồn tại")
    void changeUsername_WhenUsernameExists_ShouldThrowInvalidCredentials() {
        RecoveryToken recoveryToken = new RecoveryToken(inactiveUser, "tok-inactive", "inactive@example.com");
        var auth = CredentialChangeAuthorization.Authorization.recovery(recoveryToken);

        ChangeUsernameRequest request = new ChangeUsernameRequest();
        request.setToken("tok-inactive");
        request.setNewUsername("already_taken");

        when(credentialChangeAuthorization.resolveFromRecoveryTokenOrSession("tok-inactive")).thenReturn(auth);
        when(userRepository.findByName("already_taken")).thenReturn(Optional.of(activeUser));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.changeUsername(request));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("đã tồn tại"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword với token của user INACTIVE: Bị chặn với lỗi ACCESS_DENIED qua validatePasswordResetPermission")
    void resetPassword_WhenInactiveToken_ShouldThrowAccessDenied() {
        RecoveryToken recoveryToken = new RecoveryToken(inactiveUser, "tok-inactive", "inactive@example.com");
        var auth = CredentialChangeAuthorization.Authorization.recovery(recoveryToken);

        when(credentialChangeAuthorization.resolveFromRecoveryToken("tok-inactive")).thenReturn(auth);
        doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "Tài khoản chưa được kích hoạt."))
                .when(credentialChangeAuthorization).validatePasswordResetPermission(auth);

        AccountVerificationRequest request = new AccountVerificationRequest();
        request.setNewPassword("newPass123");
        request.setConfirmPassword("newPass123");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.resetPassword("tok-inactive", request));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        verify(userRepository, never()).save(any());
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
