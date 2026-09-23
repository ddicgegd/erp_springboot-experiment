package com.ddicg.erp.modules.iam.service;


import com.ddicg.erp.modules.iam.dto.request.AccountVerificationRequest;
import com.ddicg.erp.modules.iam.dto.request.ChangeUsernameRequest;
import com.ddicg.erp.modules.iam.dto.request.RefreshTokenRequest;
import com.ddicg.erp.modules.iam.dto.request.UpdateProfileRequest;
import com.ddicg.erp.modules.iam.dto.request.UserLoginRequest;
import com.ddicg.erp.modules.iam.dto.request.UserRegisterRequest;
import com.ddicg.erp.modules.iam.dto.response.AuthResponse;
import com.ddicg.erp.modules.iam.dto.response.MyProfileResponse;
import com.ddicg.erp.modules.iam.dto.response.RegisterResponse;
import com.ddicg.erp.modules.iam.dto.UserDto;
import com.ddicg.erp.core.common.dto.response.Response;
import jakarta.servlet.http.HttpServletRequest;

public interface iUser {
    Response<RegisterResponse> createUser(final UserRegisterRequest body);
    Response<AuthResponse> loginUser(final UserLoginRequest body);
    Response<String> verifyEmail(final String code);
    Response<String> resetPassword(final String code, final AccountVerificationRequest request);
    Response<String> recoverAccount(final String email);
    Response<String> validateResetToken(final String token);
    Response<AuthResponse> refreshToken(final RefreshTokenRequest request);
//    Page<UserDto> search(final UserSearchRequest request);
//    Page<UserSearchRequest> search(final UserSearchRequest request);
    void logoutUser(HttpServletRequest request);
    Response<MyProfileResponse> getMyProfile();
    Response<MyProfileResponse> updateMyProfile(final UpdateProfileRequest request);
    Response<MyProfileResponse> uploadAvatar(final org.springframework.web.multipart.MultipartFile file);
    Response<String> changeUsername(final ChangeUsernameRequest request);
}