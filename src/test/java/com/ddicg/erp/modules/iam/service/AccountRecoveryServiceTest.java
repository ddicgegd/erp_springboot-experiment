package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RecoveryTokenStore recoveryTokenStore;

    @InjectMocks
    private AccountRecoveryService accountRecoveryService;

    private User activeUser;
    private User inactiveUser;
    private User lockedUser;

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

        lockedUser = User.builder()
                .name("locked_user")
                .email("locked@example.com")
                .status(ActiveStatus.LOCKED)
                .build();
        lockedUser.setId(3L);
    }

    @Test
    @DisplayName("Issue token cho user ACTIVE: Cấp token mới với TTL 20 phút")
    void issue_WhenUserIsActive_ShouldSet20MinutesTtl() {
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(activeUser));

        RecoveryToken recoveryToken = accountRecoveryService.issue("active@example.com");

        assertNotNull(recoveryToken);
        assertEquals("active@example.com", recoveryToken.email());
        assertEquals(activeUser, recoveryToken.user());
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(recoveryTokenStore).save(eq("active@example.com"), eq(recoveryToken.token()), ttlCaptor.capture());
        assertEquals(Duration.ofMinutes(20), ttlCaptor.getValue());
    }

    @Test
    @DisplayName("Issue token cho user INACTIVE: Ném ngoại lệ INVALID_CREDENTIALS")
    void issue_WhenUserIsInactive_ShouldThrowInvalidCredentials() {
        when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(inactiveUser));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountRecoveryService.issue("inactive@example.com"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("chưa được kích hoạt"));
        verifyNoInteractions(recoveryTokenStore);
    }

    @Test
    @DisplayName("Issue token cho user LOCKED: Ném ngoại lệ INVALID_CREDENTIALS")
    void issue_WhenUserIsLocked_ShouldThrowInvalidCredentials() {
        when(userRepository.findByEmail("locked@example.com")).thenReturn(Optional.of(lockedUser));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountRecoveryService.issue("locked@example.com"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("khóa"));
        verifyNoInteractions(recoveryTokenStore);
    }

    @Test
    @DisplayName("Issue token cho user không tồn tại: Ném ngoại lệ USER_NOT_FOUND")
    void issue_WhenUserNotFound_ShouldThrowUserNotFound() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountRecoveryService.issue("notfound@example.com"));

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(recoveryTokenStore);
    }

    @Test
    @DisplayName("Resolve token hợp lệ: Trả về RecoveryToken đúng thông tin")
    void resolve_WhenValidToken_ShouldReturnRecoveryToken() {
        when(recoveryTokenStore.findEmailByToken("valid-token")).thenReturn(Optional.of("active@example.com"));
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(activeUser));

        RecoveryToken recoveryToken = accountRecoveryService.resolve("valid-token");

        assertNotNull(recoveryToken);
        assertEquals("valid-token", recoveryToken.token());
        assertEquals("active@example.com", recoveryToken.email());
        assertEquals(activeUser, recoveryToken.user());
    }

    @Test
    @DisplayName("Resolve token rỗng: Ném ngoại lệ INVALID_CREDENTIALS")
    void resolve_WhenBlankToken_ShouldThrowInvalidCredentials() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountRecoveryService.resolve("   "));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    @DisplayName("Resolve token không tồn tại trong store: Ném ngoại lệ INVALID_CREDENTIALS")
    void resolve_WhenTokenNotFound_ShouldThrowInvalidCredentials() {
        when(recoveryTokenStore.findEmailByToken("expired-token")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountRecoveryService.resolve("expired-token"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    @DisplayName("Resolve token thuộc user LOCKED: Ném ngoại lệ INVALID_CREDENTIALS")
    void resolve_WhenUserIsLocked_ShouldThrowInvalidCredentials() {
        when(recoveryTokenStore.findEmailByToken("locked-token")).thenReturn(Optional.of("locked@example.com"));
        when(userRepository.findByEmail("locked@example.com")).thenReturn(Optional.of(lockedUser));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountRecoveryService.resolve("locked-token"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("khóa"));
    }

    @Test
    @DisplayName("Resolve token thuộc user INACTIVE: Ném ngoại lệ INVALID_CREDENTIALS")
    void resolve_WhenUserIsInactive_ShouldThrowInvalidCredentials() {
        when(recoveryTokenStore.findEmailByToken("inactive-token")).thenReturn(Optional.of("inactive@example.com"));
        when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(inactiveUser));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountRecoveryService.resolve("inactive-token"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("chưa được kích hoạt"));
    }

    @Test
    @DisplayName("Consume token: Gọi store để xóa token và email")
    void consume_ShouldDelegateToStore() {
        RecoveryToken token = new RecoveryToken(activeUser, "my-token", "active@example.com");

        accountRecoveryService.consume(token);

        verify(recoveryTokenStore).consume("active@example.com", "my-token");
    }
}
