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
import com.ddicg.erp.modules.iam.dto.request.UpdateProfileRequest;
import com.ddicg.erp.modules.iam.dto.response.MyProfileResponse;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceBirthdayTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityUtil securityUtil;

    @Mock
    private RedisService redisService;

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
                .fullName("Test User")
                .phoneNumber("0901234567")
                .status(ActiveStatus.ACTIVE)
                .rank(UserRank.MEMBER)
                .build();
    }

    @Test
    @DisplayName("getMyProfile: Trả về thông tin dateOfBirth chính xác")
    void getMyProfile_ShouldReturnDateOfBirthAndProfileData() {
        LocalDate birthDate = LocalDate.of(2000, 5, 15);
        testUser.setDateOfBirth(birthDate);

        when(securityUtil.getCurrentUsername()).thenReturn(testEmail);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        Response<MyProfileResponse> response = userService.getMyProfile();

        assertNotNull(response);
        MyProfileResponse profile = response.getData();
        assertNotNull(profile);
        assertEquals(birthDate, profile.getDateOfBirth());
    }

    @Test
    @DisplayName("getMyProfile: Khi đang trong hạn đổi username (Redis có key), trả về usernameCooldownUntil thời gian hết hạn")
    void getMyProfile_WhenUsernameInCooldown_ShouldReturnCooldownInfo() {
        when(securityUtil.getCurrentUsername()).thenReturn(testEmail);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        // Giả lập còn 864,000 giây (10 ngày)
        when(redisService.getExpireSeconds(RedisTable.AUTH_GUARD_COOLDOWN.key(testUser.getId()))).thenReturn(864000L);

        Response<MyProfileResponse> response = userService.getMyProfile();

        assertNotNull(response);
        MyProfileResponse profile = response.getData();
        assertNotNull(profile);
        assertNotNull(profile.getUsernameCooldownUntil());
    }
    @Test
    @DisplayName("updateMyProfile: Cập nhật ngày sinh hợp lệ lần đầu thành công")
    void updateMyProfile_WhenFirstTimeUpdatingDob_ShouldSucceed() {
        LocalDate validDob = LocalDate.now().minusYears(22);
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .dateOfBirth(validDob)
                .build();

        when(securityUtil.getCurrentUsername()).thenReturn(testEmail);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Response<MyProfileResponse> response = userService.updateMyProfile(request);

        assertNotNull(response);
        MyProfileResponse profile = response.getData();
        assertEquals(validDob, profile.getDateOfBirth());
        assertNotNull(testUser.getDobUpdatedAt());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    @DisplayName("updateMyProfile: Ném ngoại lệ VALIDATION_FAILED khi ngày sinh < 10 tuổi")
    void updateMyProfile_WhenAgeLessThan10_ShouldThrowValidationFailed() {
        LocalDate tooYoungDob = LocalDate.now().minusYears(5);
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .dateOfBirth(tooYoungDob)
                .build();

        when(securityUtil.getCurrentUsername()).thenReturn(testEmail);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.updateMyProfile(request));
        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("updateMyProfile: Ném ngoại lệ VALIDATION_FAILED khi ngày sinh > 120 tuổi")
    void updateMyProfile_WhenAgeGreaterThan120_ShouldThrowValidationFailed() {
        LocalDate tooOldDob = LocalDate.now().minusYears(130);
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .dateOfBirth(tooOldDob)
                .build();

        when(securityUtil.getCurrentUsername()).thenReturn(testEmail);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.updateMyProfile(request));
        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("updateMyProfile: Ném ngoại lệ INVALID_CREDENTIALS khi cập nhật lại ngày sinh trong vòng 365 ngày (Cooldown)")
    void updateMyProfile_WhenUpdatingWithinCooldown_ShouldThrowInvalidCredentials() {
        LocalDate originalDob = LocalDate.of(1995, 5, 20);
        testUser.setDateOfBirth(originalDob);
        testUser.setDobUpdatedAt(LocalDateTime.now().minusDays(100)); // Đã đổi 100 ngày trước

        LocalDate newDob = LocalDate.of(1996, 6, 15);
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .dateOfBirth(newDob)
                .build();

        when(securityUtil.getCurrentUsername()).thenReturn(testEmail);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.updateMyProfile(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("tối đa 1 lần mỗi năm"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("updateMyProfile: Cập nhật ngày sinh thành công sau khi đã qua 365 ngày cooldown")
    void updateMyProfile_WhenUpdatingAfterCooldown_ShouldSucceed() {
        LocalDate originalDob = LocalDate.of(1995, 5, 20);
        testUser.setDateOfBirth(originalDob);
        testUser.setDobUpdatedAt(LocalDateTime.now().minusDays(370)); // Đã qua 370 ngày (> 365 ngày)

        LocalDate newDob = LocalDate.of(1996, 6, 15);
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .dateOfBirth(newDob)
                .build();

        when(securityUtil.getCurrentUsername()).thenReturn(testEmail);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Response<MyProfileResponse> response = userService.updateMyProfile(request);

        assertNotNull(response);
        MyProfileResponse profile = response.getData();
        assertEquals(newDob, profile.getDateOfBirth());
        verify(userRepository, times(1)).save(testUser);
    }
}
