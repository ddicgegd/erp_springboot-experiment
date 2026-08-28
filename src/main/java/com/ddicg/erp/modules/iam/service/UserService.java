package com.ddicg.erp.modules.iam.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.core.event.domainevent.SaveDeviceInfo;
import com.ddicg.erp.core.event.domainevent.AccountRecoveryEvent;
import com.ddicg.erp.core.event.domainevent.VerificationEmailEvent;
import com.ddicg.erp.modules.merchandise.mapper.UserMapper;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.dto.UserDto;
import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.core.common.model.enums.RoleType;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.core.event.service.ActiveLogService;
import com.ddicg.erp.modules.iam.service.JwtService;
import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.modules.iam.service.AccountRecoveryService;
import com.ddicg.erp.modules.iam.dto.request.AccountVerificationRequest;
import com.ddicg.erp.modules.iam.dto.request.ChangeUsernameRequest;
import com.ddicg.erp.modules.iam.dto.request.RefreshTokenRequest;
import com.ddicg.erp.modules.iam.dto.request.UpdateProfileRequest;
import com.ddicg.erp.modules.iam.dto.request.UserLoginRequest;
import com.ddicg.erp.modules.iam.dto.request.UserRegisterRequest;
import com.ddicg.erp.modules.iam.dto.response.AuthResponse;
import com.ddicg.erp.modules.iam.dto.response.DeviceInfoResponse;
import com.ddicg.erp.modules.iam.dto.response.MyProfileResponse;
import com.ddicg.erp.modules.iam.dto.response.RegisterResponse;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.iam.service.DeviceInfoService;
import com.ddicg.erp.core.common.service.MinioService;
import org.springframework.web.multipart.MultipartFile;
import com.ddicg.erp.modules.iam.service.RefreshTokenService;
import com.ddicg.erp.core.security.SecurityUtil;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.ddicg.erp.modules.iam.service.iUser;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements iUser {


  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final Helper helper;
  private final ApplicationEventPublisher eventPublisher;
  private final DeviceInfoService deviceInfoService;
  private final RefreshTokenService refreshTokenService;
  private final UserDetailsService userDetailsService;
  private static final int OTP_LENGTH = 6;
  private static final int OTP_UPPER_BOUND = (int) Math.pow(10, OTP_LENGTH);
  private static final String OTP_FORMAT_PATTERN = "%0" + OTP_LENGTH + "d";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  @Value("${frontend.url}")
  private String frontendUrl;
  private final UserMapper userMapper;
  private final ActiveLogService activeLogService;
  private final RedisService redisService;
  private final JwtService jwtService;
  private final ObjectMapper objectMapper;
  private final SecurityUtil securityUtil;
  private final MinioService minioService;
  private final CredentialChangeAuthorization credentialChangeAuthorization;
  private final AccountRecoveryService accountRecoveryService;

  @Override
  @Transactional
  public Response<RegisterResponse> createUser(UserRegisterRequest body) {

    if (!helper.isEmailFormat(body.getEmail())) {
      throw new BusinessException(ErrorCode.INVALID_FORMAT, "Email không đúng định dạng");
    }
    if (!body.getPassword().equals(body.getConfirmPassword())) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Mật khẩu không khớp");
    }

    userRepository.findByEmail(body.getEmail()).ifPresent(existingEmailUser -> {
      if (existingEmailUser.getStatus() == ActiveStatus.ACTIVE) {
        throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Email đã tồn tại.");
      } else if (existingEmailUser.getStatus() == ActiveStatus.INACTIVE) {
        if (!existingEmailUser.getName().equals(body.getName())) {
          throw new BusinessException(ErrorCode.REGISTRATION_INFO_MISMATCH,
              "Email này đã được đăng ký nhưng thông tin đăng ký hiện tại không khớp! Bạn có thể sử dụng chức năng Khôi phục thông tin tài khoản.");
        }
      }
    });

    userRepository.findByName(body.getName()).ifPresent(existingUser -> {
      if (!existingUser.getEmail().equals(body.getEmail())) {
        throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Tên đăng nhập đã tồn tại với email khác.")
            .with("email", helper.maskEmail(existingUser.getEmail()));
      }
    });

    Optional<User> optionalUser = userRepository.findByNameAndEmail(body.getName(), body.getEmail());

    User user;
    boolean someCondition;
    if (optionalUser.isPresent()) {
      user = optionalUser.get();
      someCondition = true;
    } else {
      user = new User();
      user.setFullName(body.getFullName());
      user.setName(body.getName());
      user.setEmail(body.getEmail());
      user.setRoles(Collections.singleton(RoleType.USER));
      user.setPassword(passwordEncoder.encode(body.getPassword()));
      user.setStatus(ActiveStatus.INACTIVE);

      user.setCreatedAt(LocalDateTime.now());
      someCondition = false;

      log.info("Tạo user mới: {}", user.getName());
    }

    String code = UUID.randomUUID().toString();
    redisService.setValueWithExpiry(RedisTable.AUTH_OTP_VERIFICATION, code, user.getEmail(), 5, TimeUnit.MINUTES);

    userRepository.save(user);

    eventPublisher.publishEvent(
        VerificationEmailEvent.builder()
            .emailVerificationToken(code)
            .email(user.getEmail())
            .username(user.getName())
            .purpose(ActiveStatus.EMAIL_VERIFICATION)
            .build());
    return Response.ok(RegisterResponse.builder()
        .message(someCondition
            ? String.format(
                "Email đã tồn tại nhưng chưa xác thực. Một email xác thực mới đã được gửi đến %s. Vui lòng kiểm tra.",
                helper.maskEmail(user.getEmail()))
            : String.format("Một email xác thực đã được gửi đến %s. Vui lòng kiểm tra.",
                helper.maskEmail(user.getEmail())))
        .build());
  }

  @Override
  @Transactional
  public Response<AuthResponse> loginUser(final UserLoginRequest body) {

    User user;
    String usernameOrEmail = body.getUsernameOrEmail();

    if (helper.isEmailFormat(usernameOrEmail)) {
        user = userRepository.findByEmail(usernameOrEmail)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Email không tồn tại."));
    } else {
        user = userRepository.findByName(usernameOrEmail)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Tên đăng nhập không tồn tại."));
    }

    if (user.getStatus().equals(ActiveStatus.INACTIVE)) { // check status
      String code = UUID.randomUUID().toString();
      redisService.setValueWithExpiry(RedisTable.AUTH_OTP_VERIFICATION, code, user.getEmail(), 5, TimeUnit.MINUTES);
      log.info("Tạo và gửi lại token xác thực cho user chưa active: {}", user.getUsername());

      eventPublisher.publishEvent(VerificationEmailEvent.builder()
          .emailVerificationToken(code).email(user.getEmail())
          .username(user.getUsername())
          .purpose(ActiveStatus.EMAIL_VERIFICATION)
          .build());

      return Response.loginResponse(HttpStatus.UNAUTHORIZED,
          AuthResponse.builder()
               .message("Tài khoản chưa được xác thực. Một email xác thực đã được gửi (lại) đến "
                   + helper.maskEmail(user.getEmail()) + ". Vui lòng kiểm tra.")
               .email(user.getEmail())
               .build());
    }

    if (!passwordEncoder.matches(body.getPassword(), user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Mật khẩu không đúng.");
    }
    String deviceId = deviceInfoService.createDeviceId(body.getDeviceInfo());
    var userDetails = userDetailsService.loadUserByUsername(user.getUsername());
    DeviceInfoResponse result = refreshTokenService.handleLoginTokens(user, userDetails, body.getDeviceInfo(), deviceId);

    return Response.ok(AuthResponse.builder()
        .message(result.getMessage() != null ? result.getMessage() : "Đăng nhập thành công.")
        .username(user.getUsername()).email(user.getEmail())
        .accessToken(result.getAccessToken())
        .refreshToken(result.getFinalRefreshTokenString())
        .avatarUrl(user.getAvatarUrl())
        .gender(user.getGender())
        .phoneNumber(user.getPhoneNumber())
        .roles(user.getRoles())
        .build());
  }

  @Override
  @Transactional
  public Response<String> verifyEmail(@NonNull final String code) {
    String email = (String) redisService.getValue(RedisTable.AUTH_OTP_VERIFICATION, code);
    if (email == null) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Mã xác thực email không hợp lệ hoặc đã hết hạn.");
    }

    User user = userRepository.findByEmail(email)
        .orElseThrow(
            () -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Người dùng không tồn tại để xác thực."));

    user.setStatus(ActiveStatus.ACTIVE);
    userRepository.save(user);
    
    redisService.delete(RedisTable.AUTH_OTP_VERIFICATION, code);
    log.info("Xác thực email thành công cho user: {}", user.getUsername());

    return Response.ok("Xác thực email thành công. Tài khoản của bạn đã được kích hoạt.");
  }

  @Override
  @Transactional
  public Response<String> resetPassword(
      @NonNull final String code,
      @NonNull final AccountVerificationRequest request) {

    var authorization = credentialChangeAuthorization.resolveFromRecoveryToken(code);
    User user = authorization.user();
    changePassword(user, request);
    credentialChangeAuthorization.consumeRecoveryToken(authorization);
    refreshTokenService.revokeAllUserTokens(user.getId());
    log.info("Đổi mật khẩu thành công cho user: {}", user.getUsername());

    return Response.ok("Mật khẩu đã được thay đổi thành công. Vui lòng đăng nhập lại.");
  }

  private void changePassword(User user, AccountVerificationRequest request) {
    if (request.getNewPassword() == null || request.getConfirmPassword() == null) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Dữ liệu mật khẩu mới bị thiếu.");
    }
    if (!request.getNewPassword().equals(request.getConfirmPassword())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Mật khẩu xác nhận không trùng khớp.");
    }

    user.setPassword(passwordEncoder.encode(request.getNewPassword()));
    userRepository.save(user);
  }

  @Override
  @Transactional
  public Response<String> recoverAccount(String email) {
    var recoveryToken = accountRecoveryService.issue(email);

    eventPublisher.publishEvent(AccountRecoveryEvent.builder()
        .user(recoveryToken.user())
        .token(recoveryToken.token())
        .build());

    log.info("Đã gửi đường dẫn khôi phục tài khoản cho người dùng: {}", recoveryToken.user().getUsername());

    return Response
        .ok("Đường dẫn khôi phục tài khoản đã được gửi đến " + helper.maskEmail(email) + ". Vui lòng kiểm tra.");
  }

  @Override
  public Response<UserDto> validateResetToken(@NonNull final String token) {
    var recoveryToken = accountRecoveryService.resolve(token);
    UserDto dto = userMapper.toDto(recoveryToken.user());
    dto.setId(null);
    return Response.ok(dto);
  }

  // @Override
  // public Page<UserDto> search(@NonNull final UserSearchRequest request) {
  // return userRepository.findAll(request.specification(), request.getPaging()
  // .pageable()).map(userMapper::toDto);
  // }

  @Override
  @Transactional
  public Response<AuthResponse> refreshToken(@NonNull final RefreshTokenRequest request) {
    final String refreshToken = request.getRefreshToken();
    final String username = jwtService.extractUsername(refreshToken);

    User user = userRepository.findByEmail(username)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
            "Người dùng không tồn tại."));

    if (!jwtService.isTokenValid(refreshToken, user)) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Refresh token không hợp lệ hoặc đã hết hạn.");
    }

    Map<Object, Object> allDeviceTokens = redisService.hGetAll(RedisTable.AUTH_SESSION_DEVICE, user.getId());

    if (allDeviceTokens == null || allDeviceTokens.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Refresh token không tồn tại hoặc đã bị thu hồi.");
    }

    String matchedDeviceId = null;
    Map<String, Object> matchedTokenData = null;

    for (Map.Entry<Object, Object> entry : allDeviceTokens.entrySet()) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> tokenData = objectMapper.convertValue(
            entry.getValue(), new TypeReference<Map<String, Object>>() {
            });
        if (refreshToken.equals(tokenData.get("token"))) {
          matchedDeviceId = (String) entry.getKey();
          matchedTokenData = tokenData;
          break;
        }
      } catch (Exception e) {
        log.warn("Không thể parse token data cho device: {}", entry.getKey());
      }
    }

    if (matchedDeviceId == null || matchedTokenData == null) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Refresh token không khớp với bất kỳ thiết bị nào.");
    }

    String newDeviceId = deviceInfoService.createDeviceId(request.getDeviceInfo());
    var userDetails = userDetailsService.loadUserByUsername(user.getUsername());
    DeviceInfoResponse result = refreshTokenService.refreshSessionTokens(
        user, userDetails, request.getDeviceInfo(), newDeviceId, matchedDeviceId);

    log.info("Refresh token thành công cho user: {}", user.getUsername());

    return Response.ok(AuthResponse.builder()
        .message(result.getMessage() != null ? result.getMessage() : "Tạo mới token thành công.")
        .accessToken(result.getAccessToken())
        .refreshToken(result.getFinalRefreshTokenString())
        .username(user.getUsername())
        .email(user.getEmail())
        .avatarUrl(user.getAvatarUrl())
        .gender(user.getGender())
        .phoneNumber(user.getPhoneNumber())
        .roles(user.getRoles())
        .build());
  }

  @Override
  public void logoutUser(HttpServletRequest request) {
    final String authHeader = request.getHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return;
    }

    final String jwt = authHeader.substring(7);

    try {
      final String username = jwtService.extractUsername(jwt);
      if (username == null)
        return;

      userRepository.findByEmail(username).ifPresent(user -> {
        refreshTokenService.revokeAllUserTokens(user.getId());
        log.info("Người dùng {} đã đăng xuất, đã thu hồi tất cả Refresh Token và Session.", username);
      });
    } catch (Exception e) {
      log.warn("Không thể thu hồi Refresh Token khi logout: {}", e.getMessage());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Response<MyProfileResponse> getMyProfile() {
    String email = securityUtil.getCurrentUsername();

    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Người dùng không tồn tại."));

    return Response.ok(MyProfileResponse.builder()
        .username(user.getName())
        .fullName(user.getFullName())
        .email(user.getEmail())
        .phoneNumber(user.getPhoneNumber())
        .avatarUrl(user.getAvatarUrl())
        .dateOfBirth(user.getDateOfBirth())
        .gender(user.getGender())
        .rank(user.getRank())
        .status(user.getStatus())
        .roles(user.getRoles())
        .build());
  }

  @Override
  @Transactional
  public Response<MyProfileResponse> updateMyProfile(final UpdateProfileRequest request) {
    String email = securityUtil.getCurrentUsername();

    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Người dùng không tồn tại."));

    if (request.getFullName() != null) {
      user.setFullName(request.getFullName());
    }
    if (request.getPhoneNumber() != null) {
      user.setPhoneNumber(request.getPhoneNumber());
    }
    if (request.getDateOfBirth() != null) {
      user.setDateOfBirth(request.getDateOfBirth());
    }
    if (request.getGender() != null) {
      user.setGender(request.getGender());
    }
    if (request.getAvatarUrl() != null) {
      user.setAvatarUrl(request.getAvatarUrl());
    }

    userRepository.save(user);
    log.info("Cập nhật thông tin profile thành công cho user: {}", user.getUsername());

    return Response.ok(MyProfileResponse.builder()
        .username(user.getName())
        .fullName(user.getFullName())
        .email(user.getEmail())
        .phoneNumber(user.getPhoneNumber())
        .avatarUrl(user.getAvatarUrl())
        .dateOfBirth(user.getDateOfBirth())
        .gender(user.getGender())
        .rank(user.getRank())
        .status(user.getStatus())
        .roles(user.getRoles())
        .build());
  }

  @Override
  @Transactional
  public Response<MyProfileResponse> uploadAvatar(final MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tệp tải lên không được để trống!");
    }

    String contentType = file.getContentType();
    if (contentType == null || !contentType.startsWith("image/")) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chỉ cho phép tải lên tệp ảnh!");
    }

    String originalFilename = file.getOriginalFilename();
    if (originalFilename != null && originalFilename.contains(".")) {
      String ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
      if (!ext.equals(".jpg") && !ext.equals(".jpeg") && !ext.equals(".png") && !ext.equals(".gif")) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Định dạng ảnh không được hỗ trợ!");
      }
    }

    String email = securityUtil.getCurrentUsername();
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Người dùng không tồn tại."));

    String oldAvatar = user.getAvatarUrl();
    if (oldAvatar != null && !oldAvatar.isEmpty() && !oldAvatar.startsWith("http")) {
      try {
        minioService.deleteFile(oldAvatar);
      } catch (Exception e) {
        log.error("Không thể xóa avatar cũ {} trên MinIO: {}", oldAvatar, e.getMessage());
      }
    }

    String newAvatarName;
    try {
      newAvatarName = minioService.uploadFile(file);
    } catch (Exception e) {
      log.error("Lỗi khi tải ảnh lên MinIO: {}", e.getMessage());
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không thể tải ảnh lên hệ thống.");
    }

    user.setAvatarUrl(newAvatarName);
    userRepository.save(user);
    log.info("Cập nhật avatar thành công cho user: {}, file mới: {}", user.getUsername(), newAvatarName);

    return Response.ok(MyProfileResponse.builder()
        .username(user.getName())
        .fullName(user.getFullName())
        .email(user.getEmail())
        .phoneNumber(user.getPhoneNumber())
        .avatarUrl(user.getAvatarUrl())
        .dateOfBirth(user.getDateOfBirth())
        .gender(user.getGender())
        .rank(user.getRank())
        .status(user.getStatus())
        .roles(user.getRoles())
        .build(), "Cập nhật ảnh đại diện thành công.");
  }

  @Override
  @Transactional
  public Response<String> changeUsername(@NonNull final ChangeUsernameRequest request) {
    var authorization = credentialChangeAuthorization.resolveFromRecoveryTokenOrSession(request.getToken());
    User user = authorization.user();

    if (redisService.hasKey(RedisTable.AUTH_GUARD_COOLDOWN, user.getId())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Bạn chỉ được đổi tên đăng nhập tối đa 1 lần mỗi tháng. Vui lòng quay lại sau.");
    }

    String newUsername = request.getNewUsername();
    if (userRepository.findByName(newUsername).isPresent()) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Tên đăng nhập mới đã tồn tại trên hệ thống.");
    }

    user.setName(newUsername);
    userRepository.save(user);

    // Đặt cooldown 30 ngày trên Redis
    redisService.setValueWithExpiry(RedisTable.AUTH_GUARD_COOLDOWN, user.getId(), "true", 30, TimeUnit.DAYS);
    credentialChangeAuthorization.consumeRecoveryToken(authorization);
    refreshTokenService.revokeAllUserTokens(user.getId());
    log.info("Người dùng ID {} đã đổi tên đăng nhập thành công sang {}", user.getId(), newUsername);

    return Response.ok("Đổi tên đăng nhập thành công. Vui lòng đăng nhập lại.");
  }
}