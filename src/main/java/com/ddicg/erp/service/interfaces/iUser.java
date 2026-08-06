package com.ddicg.erp.service.interfaces;


import com.ddicg.erp.service.dto.request.AccountVerificationRequest;
import com.ddicg.erp.service.dto.request.ChangeUsernameRequest;
import com.ddicg.erp.service.dto.request.RefreshTokenRequest;
import com.ddicg.erp.service.dto.request.UpdateProfileRequest;
import com.ddicg.erp.service.dto.request.UserLoginRequest;
import com.ddicg.erp.service.dto.request.UserRegisterRequest;
import com.ddicg.erp.service.dto.response.AuthResponse;
import com.ddicg.erp.service.dto.response.MyProfileResponse;
import com.ddicg.erp.service.dto.response.RegisterResponse;
import com.ddicg.erp.service.dto.UserDto;
import com.ddicg.erp.service.dto.response.ResponseConfig.Response;
import jakarta.servlet.http.HttpServletRequest;

public interface iUser {
    Response<RegisterResponse> createUser(final UserRegisterRequest body);
    Response<AuthResponse> loginUser(final UserLoginRequest body);
    Response<String> verifyEmail(final String code);
    Response<String> resetPassword(final String code, final AccountVerificationRequest request);
    Response<String> recoverAccount(final String email);
    Response<UserDto> validateResetToken(final String token);
    Response<AuthResponse> refreshToken(final RefreshTokenRequest request);
//    Page<UserDto> search(final UserSearchRequest request);
//    Page<UserSearchRequest> search(final UserSearchRequest request);
    void logoutUser(HttpServletRequest request);
    Response<MyProfileResponse> getMyProfile();
    Response<MyProfileResponse> updateMyProfile(final UpdateProfileRequest request);
    Response<MyProfileResponse> uploadAvatar(final org.springframework.web.multipart.MultipartFile file);
    Response<String> changeUsername(final ChangeUsernameRequest request);
}