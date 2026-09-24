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
class AccountVerificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenStore verificationTokenStore;

    @InjectMocks
    private AccountVerificationService accountVerificationService;

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
    @DisplayName("Issue token cho user INACTIVE: Cấp token mới với TTL 15 phút")
    void issue_WhenUserIsInactive_ShouldSet15MinutesTtl() {
        when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(inactiveUser));

        VerificationToken verificationToken = accountVerificationService.issue("inactive@example.com");

        assertNotNull(verificationToken);
        assertEquals("inactive@example.com", verificationToken.email());
        assertEquals(inactiveUser, verificationToken.user());
        assertNotNull(verificationToken.token());

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(verificationTokenStore).save(eq("inactive@example.com"), eq(verificationToken.token()), ttlCaptor.capture());
        assertEquals(Duration.ofMinutes(15), ttlCaptor.getValue());
    }

    @Test
    @DisplayName("Issue token cho user ACTIVE: Ném ngoại lệ INVALID_CREDENTIALS vì đã kích hoạt")
    void issue_WhenUserIsActive_ShouldThrowInvalidCredentials() {
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(activeUser));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountVerificationService.issue("active@example.com"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("đã được kích hoạt"));
        verifyNoInteractions(verificationTokenStore);
    }

    @Test
    @DisplayName("Issue token cho user LOCKED: Ném ngoại lệ INVALID_CREDENTIALS")
    void issue_WhenUserIsLocked_ShouldThrowInvalidCredentials() {
        when(userRepository.findByEmail("locked@example.com")).thenReturn(Optional.of(lockedUser));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountVerificationService.issue("locked@example.com"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("khóa"));
        verifyNoInteractions(verificationTokenStore);
    }

    @Test
    @DisplayName("Issue token cho user không tồn tại: Ném ngoại lệ USER_NOT_FOUND")
    void issue_WhenUserNotFound_ShouldThrowUserNotFound() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountVerificationService.issue("notfound@example.com"));

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(verificationTokenStore);
    }

    @Test
    @DisplayName("Verify token hợp lệ: Kích hoạt user INACTIVE lên ACTIVE, lưu DB và consume token")
    void verify_WhenValidToken_ShouldActivateUserAndConsumeToken() {
        when(verificationTokenStore.findEmailByToken("valid-token")).thenReturn(Optional.of("inactive@example.com"));
        when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(inactiveUser));

        VerificationToken verificationToken = accountVerificationService.verify("valid-token");

        assertNotNull(verificationToken);
        assertEquals(ActiveStatus.ACTIVE, inactiveUser.getStatus());
        verify(userRepository).save(inactiveUser);
        verify(verificationTokenStore).consume("inactive@example.com", "valid-token");
    }

    @Test
    @DisplayName("Verify token rỗng: Ném ngoại lệ INVALID_CREDENTIALS")
    void verify_WhenBlankToken_ShouldThrowInvalidCredentials() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountVerificationService.verify("   "));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        verifyNoInteractions(verificationTokenStore);
    }

    @Test
    @DisplayName("Verify token không tồn tại trong store: Ném ngoại lệ INVALID_CREDENTIALS")
    void verify_WhenTokenNotFound_ShouldThrowInvalidCredentials() {
        when(verificationTokenStore.findEmailByToken("expired-token")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountVerificationService.verify("expired-token"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    @DisplayName("Verify token thuộc user LOCKED: Ném ngoại lệ INVALID_CREDENTIALS")
    void verify_WhenUserIsLocked_ShouldThrowInvalidCredentials() {
        when(verificationTokenStore.findEmailByToken("locked-token")).thenReturn(Optional.of("locked@example.com"));
        when(userRepository.findByEmail("locked@example.com")).thenReturn(Optional.of(lockedUser));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountVerificationService.verify("locked-token"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("khóa"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Consume token: Gọi store để xóa token và email")
    void consume_ShouldDelegateToStore() {
        VerificationToken token = new VerificationToken(inactiveUser, "my-token", "inactive@example.com");

        accountVerificationService.consume(token);

        verify(verificationTokenStore).consume("inactive@example.com", "my-token");
    }
}
