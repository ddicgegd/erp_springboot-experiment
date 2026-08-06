package com.ddicg.erp.modules.iam.controller;
import com.ddicg.erp.core.common.dto.request.PagingRequest;


import com.ddicg.erp.modules.iam.dto.request.AccountVerificationRequest;
import com.ddicg.erp.modules.iam.dto.request.ChangeUsernameRequest;
import com.ddicg.erp.modules.iam.dto.request.RefreshTokenRequest;
import com.ddicg.erp.modules.iam.dto.request.UpdateProfileRequest;
import com.ddicg.erp.modules.iam.dto.request.UserLoginRequest;
import com.ddicg.erp.modules.iam.dto.request.UserRegisterRequest;
import com.ddicg.erp.modules.iam.dto.response.*;
import com.ddicg.erp.modules.merchandise.dto.response.*;
import com.ddicg.erp.core.common.dto.response.*;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.iam.dto.UserDto;
import com.ddicg.erp.modules.iam.service.iUser;
import com.ddicg.erp.modules.iam.controller.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthControllerImpl implements AuthController {

    private final iUser userService;

    @Override
    public Response<AuthResponse> login(final UserLoginRequest body) {
        return userService.loginUser(body);
    }

    @Override
    public Response<RegisterResponse> register(final UserRegisterRequest body) {
        return userService.createUser(body);
    }

    @Override
    public Response<String> verifyEmail(final String code) {
        return userService.verifyEmail(code);
    }

    @Override
    public Response<String> resetPassword(
            final String code,
            final AccountVerificationRequest body) {
        return userService.resetPassword(code, body);
    }

    @Override
    public Response<UserDto> validateResetToken(final String token) {
        return userService.validateResetToken(token);
    }

    @Override
    public Response<AuthResponse> refreshToken(final RefreshTokenRequest body) {
        return userService.refreshToken(body);
    }

    @Override
    public Response<String> recoverAccount(final String email) {
        return userService.recoverAccount(email);
    }

//    @Override
//    public Response<PagingResponse<UserDto>> search(UserSearchRequest request) {
//        final Page<UserDto> users = userService.search(request);
//        final PagingRequest page = request.getPaging();
//        return Response.ok(
//                PagingResponse.<UserDto>builder()
//                        .contents(users.getContent())
//                        .paging(new PageableData()
//                                .setPageNumber(page.getPage() - 1)
//                                .setTotalPage(users.getTotalPages())
//                                .setPageSize(page.getSize())
//                                .setTotalRecord(users.getTotalElements())
//                        )
//                        .build()
//        );
//    }

    @Override
    public ResponseEntity<?> logout(HttpServletRequest request) {
        userService.logoutUser(request);
        return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công."));
    }

    @Override
    public Response<MyProfileResponse> getMyProfile() {
        return userService.getMyProfile();
    }

    @Override
    public Response<MyProfileResponse> updateMyProfile(final UpdateProfileRequest body) {
        return userService.updateMyProfile(body);
    }

    @Override
    public Response<MyProfileResponse> uploadAvatar(final org.springframework.web.multipart.MultipartFile file) {
        return userService.uploadAvatar(file);
    }

    @Override
    public Response<String> changeUsername(final ChangeUsernameRequest body) {
        return userService.changeUsername(body);
    }
}