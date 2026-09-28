package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.core.common.model.enums.Gender;
import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.iam.dto.request.UpdateCredentialsRequest;
import com.ddicg.erp.modules.iam.dto.response.CredentialActiveStatusResponse;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.notification.kafka.producer.NotificationEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceCredentialChangeTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityUtil securityUtil;

    @Mock
    private RedisService redisService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Helper helper;

    @Mock
    private CredentialTokenStore credentialTokenStore;

    @Mock
    private NotificationEventProducer notificationEventProducer;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private final String testEmail = "test@example.com";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(100L)
                .name("testuser")
                .email(testEmail)
                .password("encodedOldPassword")
                .fullName("Test User")
                .status(ActiveStatus.ACTIVE)
                .rank(UserRank.MEMBER)
                .gender(Gender.MALE)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(userService, "frontendUrl", "http://localhost:3000");
    }

    @Test
    @DisplayName("requestCredentialChange: Gửi yêu cầu đổi credentials thành công, đặt khóa lock 5 phút và bắn mail")
    void requestCredentialChange_WhenValid_ShouldIssueTokenAndLock5Minutes() {
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(testUser));
        when(credentialTokenStore.isRequestLocked(100L)).thenReturn(false);
        when(helper.maskEmail(testEmail)).thenReturn("t***@example.com");

        Response<String> response = userService.requestCredentialChange();

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertTrue(response.getStatus().getMessage().contains("t***@example.com"));
        verify(credentialTokenStore, times(1)).issueToken(eq(100L), eq(testEmail), anyString(), eq(Duration.ofMinutes(5)));
        org.mockito.ArgumentCaptor<String> urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(notificationEventProducer, times(1)).sendCredentialChangeEmail(eq(testEmail), eq("testuser"), urlCaptor.capture(), anyString());
        assertTrue(urlCaptor.getValue().startsWith("http://localhost:3000/credential-change/activate?token="));
    }

    @Test
    @DisplayName("requestCredentialChange: Ném TOO_MANY_REQUESTS khi yêu cầu trước đó vẫn còn trong hạn lock 5 phút")
    void requestCredentialChange_WhenLocked_ShouldThrowTooManyRequests429() {
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(testUser));
        when(credentialTokenStore.isRequestLocked(100L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.requestCredentialChange());
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("vẫn đang có hiệu lực"));
        verify(credentialTokenStore, never()).issueToken(any(), any(), any(), any());
        verify(notificationEventProducer, never()).sendCredentialChangeEmail(any(), any(), any(), any());
    }

    @Test
    @DisplayName("requestCredentialChange: Ném INVALID_CREDENTIALS khi tài khoản INACTIVE hoặc LOCKED")
    void requestCredentialChange_WhenUserInactive_ShouldThrowInvalidCredentials() {
        testUser.setStatus(ActiveStatus.INACTIVE);
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(testUser));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.requestCredentialChange());
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        verify(credentialTokenStore, never()).issueToken(any(), any(), any(), any());
    }

    @Test
    @DisplayName("activateCredentialToken: Kích hoạt token từ link email thành công và cấp quyền trên Redis")
    void activateCredentialToken_WhenTokenValid_ShouldActivateOnRedisForRemainingTtl() {
        String token = "valid-raw-token";
        CredentialTokenStore.TokenInfo tokenInfo = new CredentialTokenStore.TokenInfo(100L, testEmail, 280L);

        when(credentialTokenStore.activateToken(token)).thenReturn(Optional.of(tokenInfo));

        Response<String> response = userService.activateCredentialToken(token);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertTrue(response.getStatus().getMessage().contains("5 phút"));
    }

    @Test
    @DisplayName("activateCredentialToken: Ném INVALID_CREDENTIALS khi token không tồn tại hoặc đã hết hạn (0 DB query)")
    void activateCredentialToken_WhenInvalidOrExpired_ShouldThrowInvalidCredentials() {
        String token = "invalid-token";

        when(credentialTokenStore.activateToken(token)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.activateCredentialToken(token));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("getCredentialChangeStatus: Polling trả về 200 OK và số giây còn lại khi key ACTIVE tồn tại trên Redis")
    void getCredentialChangeStatus_WhenActive_ShouldReturn200WithRemainingSeconds() {
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(testUser));
        when(credentialTokenStore.getActiveRemainingSeconds(100L)).thenReturn(285L);

        Response<CredentialActiveStatusResponse> response = userService.getCredentialChangeStatus();

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("ACTIVE", response.getData().getStatus());
        assertEquals(285L, response.getData().getRemainingSeconds());
        assertNotNull(response.getData().getExpiresAt());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("getCredentialChangeStatus: Polling ném 401 INVALID_CREDENTIALS khi chưa active hoặc hết hạn (0 DB query)")
    void getCredentialChangeStatus_WhenNotActive_ShouldThrow401WithoutDbQuery() {
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(testUser));
        when(credentialTokenStore.getActiveRemainingSeconds(100L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.getCredentialChangeStatus());
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("updateCredentials: Đổi chỉ Username thành công và đặt cooldown 30 ngày")
    void updateCredentials_WhenOnlyUsernameProvided_ShouldUpdateAndSet30DayCooldown() {
        UpdateCredentialsRequest request = UpdateCredentialsRequest.builder()
                .newUsername("new_active_username")
                .build();

        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(testUser));
        when(credentialTokenStore.isUserActive(100L)).thenReturn(true);
        when(redisService.hasKey(RedisTable.AUTH_GUARD_COOLDOWN, 100L)).thenReturn(false);
        when(userRepository.findByName("new_active_username")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Response<String> response = userService.updateCredentials(request);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("new_active_username", testUser.getName());
        verify(redisService, times(1)).setValueWithExpiry(eq(RedisTable.AUTH_GUARD_COOLDOWN), eq(100L), eq("true"), eq(30L), any());
        verify(credentialTokenStore, times(1)).consumeActiveGrant(100L);
        verify(refreshTokenService, times(1)).revokeAllUserTokens(100L);
    }

    @Test
    @DisplayName("updateCredentials: Đổi chỉ Password thành công")
    void updateCredentials_WhenOnlyPasswordProvided_ShouldUpdatePassword() {
        UpdateCredentialsRequest request = UpdateCredentialsRequest.builder()
                .newPassword("NewSecurePassword123@")
                .confirmPassword("NewSecurePassword123@")
                .build();

        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(testUser));
        when(credentialTokenStore.isUserActive(100L)).thenReturn(true);
        when(passwordEncoder.encode("NewSecurePassword123@")).thenReturn("encodedNewPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Response<String> response = userService.updateCredentials(request);

        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
        assertEquals("encodedNewPassword", testUser.getPassword());
        verify(credentialTokenStore, times(1)).consumeActiveGrant(100L);
        verify(refreshTokenService, times(1)).revokeAllUserTokens(100L);
    }

    @Test
    @DisplayName("updateCredentials: Đổi cả Username và Password cùng lúc trong 1 request")
    void updateCredentials_WhenBothProvided_ShouldUpdateBothAndRevokeTokens() {
        UpdateCredentialsRequest request = UpdateCredentialsRequest.builder()
                .newUsername("brand_new_user")
                .newPassword("NewSecurePassword123@")
                .confirmPassword("NewSecurePassword123@")
                .build();

        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(testUser));
        when(credentialTokenStore.isUserActive(100L)).thenReturn(true);
        when(redisService.hasKey(RedisTable.AUTH_GUARD_COOLDOWN, 100L)).thenReturn(false);
        when(userRepository.findByName("brand_new_user")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("NewSecurePassword123@")).thenReturn("encodedNewPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Response<String> response = userService.updateCredentials(request);

        assertNotNull(response);
        assertEquals("brand_new_user", testUser.getName());
        assertEquals("encodedNewPassword", testUser.getPassword());
        verify(redisService, times(1)).setValueWithExpiry(eq(RedisTable.AUTH_GUARD_COOLDOWN), eq(100L), eq("true"), eq(30L), any());
        verify(credentialTokenStore, times(1)).consumeActiveGrant(100L);
        verify(refreshTokenService, times(1)).revokeAllUserTokens(100L);
    }

    @Test
    @DisplayName("updateCredentials: Ném 401 INVALID_CREDENTIALS khi đổi username đang trong cooldown 30 ngày")
    void updateCredentials_WhenUsernameIn30DayCooldown_ShouldThrow401() {
        UpdateCredentialsRequest request = UpdateCredentialsRequest.builder()
                .newUsername("new_name")
                .build();

        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(testUser));
        when(credentialTokenStore.isUserActive(100L)).thenReturn(true);
        when(redisService.hasKey(RedisTable.AUTH_GUARD_COOLDOWN, 100L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateCredentials(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("tối đa 1 lần mỗi tháng"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCredentials: Ném 401 khi chưa active quyền trên Redis (0 DB query)")
    void updateCredentials_WhenNotActiveOnRedis_ShouldThrow401FastWithoutDbQuery() {
        UpdateCredentialsRequest request = UpdateCredentialsRequest.builder()
                .newUsername("new_name")
                .build();

        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(testUser));
        when(credentialTokenStore.isUserActive(100L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateCredentials(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("chưa xác thực qua email"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCredentials: Ném VALIDATION_FAILED khi không cung cấp cả username lẫn password")
    void updateCredentials_WhenNeitherUsernameNorPasswordProvided_ShouldThrowValidationFailed() {
        UpdateCredentialsRequest request = UpdateCredentialsRequest.builder().build();

        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(testUser));
        when(credentialTokenStore.isUserActive(100L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateCredentials(request));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }
}
