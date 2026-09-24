package com.ddicg.erp.modules.iam.controller;

import com.ddicg.erp.modules.iam.dto.request.AccountVerificationRequest;
import com.ddicg.erp.modules.iam.dto.request.ChangeUsernameRequest;
import com.ddicg.erp.modules.iam.dto.request.RefreshTokenRequest;
import com.ddicg.erp.modules.iam.dto.request.ResendVerificationRequest;
import com.ddicg.erp.modules.iam.dto.request.UpdateProfileRequest;
import com.ddicg.erp.modules.iam.dto.request.UserLoginRequest;
import com.ddicg.erp.modules.iam.dto.request.UserRegisterRequest;
import com.ddicg.erp.modules.iam.dto.response.AuthResponse;
import com.ddicg.erp.modules.iam.dto.response.MyProfileResponse;
import com.ddicg.erp.modules.iam.dto.response.RegisterResponse;
import com.ddicg.erp.modules.iam.dto.UserDto;
import com.ddicg.erp.core.common.dto.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/auth")
public interface AuthController {

        @PostMapping("/login")
        @ResponseStatus(HttpStatus.OK)
        Response<AuthResponse> login(@Valid @RequestBody final UserLoginRequest body);

        @PostMapping("/register")
        @ResponseStatus(HttpStatus.OK)
        Response<RegisterResponse> register(@Valid @RequestBody final UserRegisterRequest body);

        @GetMapping("/verify-email")
        @ResponseStatus(HttpStatus.OK)
        Response<String> verifyEmail(@RequestParam("token") final String token);
        @PostMapping("/resend-verification")
        @ResponseStatus(HttpStatus.OK)
        Response<String> resendVerificationEmail(@Valid @RequestBody final ResendVerificationRequest body);
        @PostMapping("/reset-password")
        @ResponseStatus(HttpStatus.OK)
        Response<String> resetPassword(@Valid @RequestBody final AccountVerificationRequest body);
        @GetMapping("/validate-reset-token")
        @ResponseStatus(HttpStatus.OK)
        Response<String> validateResetToken(@RequestParam("token") final String token);

        @PostMapping("/refresh-token")
        @ResponseStatus(HttpStatus.OK)
        Response<AuthResponse> refreshToken(@Valid @RequestBody final RefreshTokenRequest body);

        @GetMapping("/recover-account/{email}")
        @ResponseStatus(HttpStatus.OK)
        Response<String> recoverAccount(@PathVariable final String email);

        @PostMapping("/logout")
        ResponseEntity<?> logout(final HttpServletRequest request);

        @GetMapping("/me")
        @ResponseStatus(HttpStatus.OK)
        @PreAuthorize("isAuthenticated()")
        Response<MyProfileResponse> getMyProfile();

        @PutMapping("/me")
        @ResponseStatus(HttpStatus.OK)
        Response<MyProfileResponse> updateMyProfile(@Valid @RequestBody final UpdateProfileRequest body);

        @PostMapping(value = "/me/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
        @ResponseStatus(HttpStatus.OK)
        Response<MyProfileResponse> uploadAvatar(@RequestParam("file") org.springframework.web.multipart.MultipartFile file);

        @PutMapping("/change-username")
        @ResponseStatus(HttpStatus.OK)
        @PreAuthorize("isAuthenticated()")
        Response<String> changeUsername(@Valid @RequestBody final ChangeUsernameRequest body);
}
