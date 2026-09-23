package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.iam.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CredentialChangeAuthorizationTest {

    @Mock
    private AccountRecoveryService accountRecoveryService;

    @Mock
    private SecurityUtil securityUtil;

    @InjectMocks
    private CredentialChangeAuthorization authorizationService;

    private User activeUser;
    private User inactiveUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .name("active_user")
                .email("active@example.com")
                .status(ActiveStatus.ACTIVE)
                .build();
        activeUser.setId(1L);

        inactiveUser = User.builder()
                .name("inactive_user")
                .email("inactive@example.com")
                .status(ActiveStatus.INACTIVE)
                .build();
        inactiveUser.setId(2L);
    }

    @Test
    @DisplayName("resolveFromRecoveryToken: Ủy quyền giải quyết token cho AccountRecoveryService")
    void resolveFromRecoveryToken_ShouldDelegateToAccountRecoveryService() {
        RecoveryToken recoveryToken = new RecoveryToken(activeUser, "token-123", "active@example.com");
        when(accountRecoveryService.resolve("token-123")).thenReturn(recoveryToken);

        var auth = authorizationService.resolveFromRecoveryToken("token-123");

        assertNotNull(auth);
        assertTrue(auth.recoveryTokenBased());
        assertEquals(activeUser, auth.user());
        assertEquals(recoveryToken, auth.recoveryToken());
    }

    @Test
    @DisplayName("resolveFromRecoveryTokenOrSession: Khi có token thì ném ACCESS_DENIED vì recovery token không được đổi username")
    void resolveFromRecoveryTokenOrSession_WithToken_ShouldThrowAccessDenied() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                authorizationService.resolveFromRecoveryTokenOrSession("token-inactive"));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("không có quyền thay đổi"));
        verifyNoInteractions(securityUtil);
    }

    @Test
    @DisplayName("resolveFromRecoveryTokenOrSession: Khi token rỗng thì lấy từ session đăng nhập")
    void resolveFromRecoveryTokenOrSession_WithoutToken_ShouldResolveFromSession() {
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(activeUser));

        var auth = authorizationService.resolveFromRecoveryTokenOrSession("");

        assertNotNull(auth);
        assertFalse(auth.recoveryTokenBased());
        assertEquals(activeUser, auth.user());
        assertNull(auth.recoveryToken());
    }

    @Test
    @DisplayName("resolveFromRecoveryTokenOrSession: Không có token và chưa đăng nhập thì ném USER_NOT_FOUND")
    void resolveFromRecoveryTokenOrSession_WithoutTokenAndNoSession_ShouldThrow() {
        when(securityUtil.getCurrentUser()).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                authorizationService.resolveFromRecoveryTokenOrSession(null));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("consumeRecoveryToken: Chỉ gọi consume khi authorization dựa trên token")
    void consumeRecoveryToken_WhenTokenBased_ShouldConsume() {
        RecoveryToken recoveryToken = new RecoveryToken(activeUser, "token-123", "active@example.com");
        var auth = CredentialChangeAuthorization.Authorization.recovery(recoveryToken);

        authorizationService.consumeRecoveryToken(auth);

        verify(accountRecoveryService).consume(recoveryToken);
    }

    @Test
    @DisplayName("consumeRecoveryToken: Không làm gì khi authorization dựa trên session")
    void consumeRecoveryToken_WhenSessionBased_ShouldNotConsume() {
        var auth = CredentialChangeAuthorization.Authorization.session(activeUser);

        authorizationService.consumeRecoveryToken(auth);

        verifyNoInteractions(accountRecoveryService);
    }

    @Test
    @DisplayName("validatePasswordResetPermission: Cho phép token tiếp tục đổi mật khẩu ngay cả khi tài khoản chưa kích hoạt")
    void validatePasswordResetPermission_WhenPendingActivation_ShouldPass() {
        RecoveryToken recoveryToken = new RecoveryToken(inactiveUser, "token-inactive", "inactive@example.com");
        var auth = CredentialChangeAuthorization.Authorization.recovery(recoveryToken);

        assertDoesNotThrow(() -> authorizationService.validatePasswordResetPermission(auth));
    }

    @Test
    @DisplayName("validatePasswordResetPermission: Cho phép token của tài khoản đã active")
    void validatePasswordResetPermission_WhenActive_ShouldPass() {
        RecoveryToken recoveryToken = new RecoveryToken(activeUser, "token-active", "active@example.com");
        var auth = CredentialChangeAuthorization.Authorization.recovery(recoveryToken);

        assertDoesNotThrow(() -> authorizationService.validatePasswordResetPermission(auth));
    }

    @Test
    @DisplayName("validatePasswordResetPermission: Cho phép phiên session-based")
    void validatePasswordResetPermission_WhenSessionBased_ShouldPass() {
        var auth = CredentialChangeAuthorization.Authorization.session(activeUser);

        assertDoesNotThrow(() -> authorizationService.validatePasswordResetPermission(auth));
    }
}
