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
import com.ddicg.erp.modules.iam.dto.request.RefreshTokenRequest;
import com.ddicg.erp.modules.iam.dto.request.ResendVerificationRequest;
import com.ddicg.erp.modules.iam.dto.request.UpdateCredentialsRequest;
import com.ddicg.erp.modules.iam.dto.request.UpdateProfileRequest;
import com.ddicg.erp.modules.iam.dto.request.UserLoginRequest;
import com.ddicg.erp.modules.iam.dto.request.UserRegisterRequest;
import com.ddicg.erp.modules.iam.dto.response.AuthResponse;
import com.ddicg.erp.modules.iam.dto.response.CredentialActiveStatusResponse;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.springframework.util.StringUtils;
import com.ddicg.erp.modules.notification.kafka.producer.NotificationEventProducer;
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
  @Value("${server.url}")
  private String serverUrl;
  private static final String RECOVERY_GENERIC_MESSAGE =
      "Nếu email tồn tại trên hệ thống, liên kết khôi phục tài khoản đã được gửi đến %s. Vui lòng kiểm tra.";
  private static final String ACTIVATION_SUCCESS_MESSAGE =
      "Tài khoản của bạn đã được kích hoạt thành công. Vui lòng thiết lập mật khẩu mới.";
  private static final String VALID_TOKEN_MESSAGE =
      "Mã token hợp lệ. Vui lòng thiết lập mật khẩu mới.";
  private final UserMapper userMapper;
  private final ActiveLogService activeLogService;
  private final RedisService redisService;
  private final JwtService jwtService;
  private final ObjectMapper objectMapper;
  private final SecurityUtil securityUtil;
  private final MinioService minioService;
  private final CredentialChangeAuthorization credentialChangeAuthorization;
  private final AccountRecoveryService accountRecoveryService;
  private final AccountVerificationService accountVerificationService;
  private final CredentialTokenStore credentialTokenStore;
  private final NotificationEventProducer notificationEventProducer;
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
              "Email này đã được đăng ký với tên đăng nhập khác nhưng chưa được kích hoạt. Vui lòng kích hoạt qua email đã gửi hoặc sử dụng tên đăng nhập đã đăng ký.");
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

    checkAndApplyVerificationRateLimit(user.getEmail());
    userRepository.save(user);

    var verificationToken = accountVerificationService.issue(user.getEmail());

    eventPublisher.publishEvent(
        VerificationEmailEvent.builder()
            .emailVerificationToken(verificationToken.token())
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

    if (user.getStatus().equals(ActiveStatus.INACTIVE)) {
      if (!redisService.hasKey(RedisTable.AUTH_VERIFICATION_COOLDOWN, user.getEmail())) {
        try {
          checkAndApplyVerificationRateLimit(user.getEmail());
          var verificationToken = accountVerificationService.issue(user.getEmail());
          log.info("Tạo và gửi lại token xác thực cho user chưa active: {}", user.getUsername());

          eventPublisher.publishEvent(VerificationEmailEvent.builder()
              .emailVerificationToken(verificationToken.token()).email(user.getEmail())
              .username(user.getUsername())
              .purpose(ActiveStatus.EMAIL_VERIFICATION)
              .build());
        } catch (BusinessException e) {
          log.warn("Bỏ qua gửi lại email xác thực do chạm hạn ngạch: {}", e.getMessage());
        }
      }
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
  public Response<String> verifyEmail(final String code) {
    if (!org.springframework.util.StringUtils.hasText(code)) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Mã xác thực email không hợp lệ hoặc đã hết hạn.");
    }

    accountVerificationService.verify(code);
    return Response.ok("Xác thực email thành công. Tài khoản của bạn đã được kích hoạt.");
  }

  @Override
  @Transactional(readOnly = true)
  public Response<String> resendVerificationEmail(@NonNull final ResendVerificationRequest request) {
    String email = request.getEmail();
    if (!helper.isEmailFormat(email)) {
      throw new BusinessException(ErrorCode.INVALID_FORMAT, "Email không đúng định dạng.");
    }

    checkAndApplyVerificationRateLimit(email);

    User user = userRepository.findByEmail(email).orElse(null);
    if (user == null || user.getStatus() != ActiveStatus.INACTIVE) {
      log.warn("Yêu cầu gửi lại email xác thực cho email không tồn tại hoặc không INACTIVE: {}", helper.maskEmail(email));
      return Response.ok(String.format("Nếu email tồn tại trên hệ thống và chưa được kích hoạt, liên kết xác thực mới đã được gửi đến %s. Vui lòng kiểm tra.", helper.maskEmail(email)));
    }

    var verificationToken = accountVerificationService.issue(email);

    eventPublisher.publishEvent(VerificationEmailEvent.builder()
        .emailVerificationToken(verificationToken.token())
        .email(user.getEmail())
        .username(user.getUsername())
        .purpose(ActiveStatus.EMAIL_VERIFICATION)
        .build());

    log.info("Đã gửi lại email xác thực cho người dùng: {}", helper.maskEmail(email));
    return Response.ok(String.format("Nếu email tồn tại trên hệ thống và chưa được kích hoạt, liên kết xác thực mới đã được gửi đến %s. Vui lòng kiểm tra.", helper.maskEmail(email)));
  }

  private void checkAndApplyVerificationRateLimit(String email) {
    if (redisService.hasKey(RedisTable.AUTH_VERIFICATION_COOLDOWN, email)) {
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh. Vui lòng thử lại sau.");
    }

    Long currentCount = redisService.increment(RedisTable.AUTH_VERIFICATION_QUOTA, email);
    if (currentCount != null && currentCount == 1L) {
      redisService.expire(RedisTable.AUTH_VERIFICATION_QUOTA, email, 3600, TimeUnit.SECONDS);
    } else {
      Long remainingTtl = redisService.getExpiry(RedisTable.AUTH_VERIFICATION_QUOTA.key(email), TimeUnit.SECONDS);
      if (remainingTtl == null || remainingTtl <= 0) {
        redisService.expire(RedisTable.AUTH_VERIFICATION_QUOTA, email, 3600, TimeUnit.SECONDS);
      }
    }

    if (currentCount != null && currentCount > 5L) {
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
          "Bạn đã vượt quá số lần yêu cầu xác thực trong 1 giờ. Vui lòng thử lại sau.");
    }

    redisService.setValueWithExpiry(RedisTable.AUTH_VERIFICATION_COOLDOWN, email, "true", 60, TimeUnit.SECONDS);
  }

  @Override
  @Transactional
  public Response<String> resetPassword(@NonNull final AccountVerificationRequest request) {
    String token = request.getToken() != null ? request.getToken().trim() : null;
    if (!org.springframework.util.StringUtils.hasText(token)) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Token khôi phục không được để trống.");
    }

    var authorization = credentialChangeAuthorization.resolveFromRecoveryToken(token);
    credentialChangeAuthorization.validatePasswordResetPermission(authorization);
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
  @Transactional(readOnly = true)
  public Response<String> recoverAccount(String email) {
    if (!helper.isEmailFormat(email)) {
      throw new BusinessException(ErrorCode.INVALID_FORMAT, "Email không đúng định dạng.");
    }

    checkAndApplyRecoveryRateLimit(email);

    User user = userRepository.findByEmail(email).orElse(null);
    if (user == null || user.getStatus() != ActiveStatus.ACTIVE) {
      log.warn("Yêu cầu khôi phục tài khoản cho email không tồn tại hoặc không ACTIVE: {}", helper.maskEmail(email));
      return Response.ok(String.format(RECOVERY_GENERIC_MESSAGE, helper.maskEmail(email)));
    }

    var recoveryToken = accountRecoveryService.issue(email);

    eventPublisher.publishEvent(AccountRecoveryEvent.builder()
        .user(recoveryToken.user())
        .email(email)
        .username(recoveryToken.user().getName())
        .token(recoveryToken.token())
        .build());

    log.info("Đã gửi đường dẫn khôi phục tài khoản cho người dùng: {}", helper.maskEmail(email));

    return Response.ok(String.format(RECOVERY_GENERIC_MESSAGE, helper.maskEmail(email)));
  }

  private void checkAndApplyRecoveryRateLimit(String email) {
    if (redisService.hasKey(RedisTable.AUTH_RECOVERY_COOLDOWN, email)) {
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh. Vui lòng thử lại sau.");
    }

    Long currentCount = redisService.increment(RedisTable.AUTH_RECOVERY_QUOTA, email);
    if (currentCount != null && currentCount == 1L) {
      redisService.expire(RedisTable.AUTH_RECOVERY_QUOTA, email, 3600, TimeUnit.SECONDS);
    } else {
      Long remainingTtl = redisService.getExpiry(RedisTable.AUTH_RECOVERY_QUOTA.key(email), TimeUnit.SECONDS);
      if (remainingTtl == null || remainingTtl <= 0) {
        redisService.expire(RedisTable.AUTH_RECOVERY_QUOTA, email, 3600, TimeUnit.SECONDS);
      }
    }

    if (currentCount != null && currentCount > 5L) {
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
          "Bạn đã vượt quá số lần yêu cầu khôi phục trong 1 giờ. Vui lòng thử lại sau.");
    }

    redisService.setValueWithExpiry(RedisTable.AUTH_RECOVERY_COOLDOWN, email, "true", 60, TimeUnit.SECONDS);
  }

  @Override
  @Transactional(readOnly = true)
  public Response<String> validateResetToken(@NonNull final String token) {
    var recoveryToken = accountRecoveryService.resolve(token);
    User user = recoveryToken.user();
    return Response.ok(user.getName(), VALID_TOKEN_MESSAGE);
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

    return Response.ok(buildMyProfileResponse(user));
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
      LocalDate newDob = request.getDateOfBirth();
      LocalDate today = LocalDate.now();

      if (newDob.isAfter(today.minusYears(10)) || newDob.isBefore(today.minusYears(120))) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Ngày sinh không hợp lệ hoặc độ tuổi phải từ 10 đến 120 tuổi.");
      }

      if (user.getDateOfBirth() != null && !user.getDateOfBirth().equals(newDob)) {
        if (user.getDobUpdatedAt() != null && user.getDobUpdatedAt().isAfter(LocalDateTime.now().minusDays(365))) {
          throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
              "Bạn chỉ được phép cập nhật ngày sinh tối đa 1 lần mỗi năm. Vui lòng liên hệ CSKH nếu cần hỗ trợ.");
        }
        user.setDobUpdatedAt(LocalDateTime.now());
      } else if (user.getDateOfBirth() == null) {
        user.setDobUpdatedAt(LocalDateTime.now());
      }

      user.setDateOfBirth(newDob);
    }
    if (request.getGender() != null) {
      user.setGender(request.getGender());
    }
    if (request.getAvatarUrl() != null) {
      user.setAvatarUrl(request.getAvatarUrl());
    }

    userRepository.save(user);
    log.info("Cập nhật thông tin profile thành công cho user: {}", user.getUsername());

    return Response.ok(buildMyProfileResponse(user));
  }

  private MyProfileResponse buildMyProfileResponse(User user) {
    // Kiểm tra Hạn đổi Username (khóa 1 tháng lưu trên Redis: AUTH_GUARD_COOLDOWN)
    LocalDateTime usernameCooldownUntil = null;
    if (user.getId() != null && redisService != null) {
      try {
        Long ttlSeconds = redisService.getExpireSeconds(RedisTable.AUTH_GUARD_COOLDOWN.key(user.getId()));
        if (ttlSeconds != null && ttlSeconds > 0) {
          usernameCooldownUntil = LocalDateTime.now().plusSeconds(ttlSeconds);
        }
      } catch (Exception e) {
        log.warn("Không thể lấy hạn đổi username từ Redis: {}", e.getMessage());
      }
    }

    return MyProfileResponse.builder()
        .id(user.getId() != null ? String.valueOf(user.getId()) : null)
        .username(user.getName())
        .fullName(user.getFullName())
        .email(user.getEmail())
        .phoneNumber(user.getPhoneNumber())
        .usernameCooldownUntil(usernameCooldownUntil)
        .avatarUrl(user.getAvatarUrl())
        .dateOfBirth(user.getDateOfBirth())
        .gender(user.getGender())
        .rank(user.getRank())
        .status(user.getStatus())
        .roles(user.getRoles())
        .build();
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
    return Response.ok(buildMyProfileResponse(user), "Cập nhật ảnh đại diện thành công.");
  }

  @Override
  @Transactional(readOnly = true)
  public Response<String> requestCredentialChange() {
    User user = securityUtil.getCurrentUser()
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Người dùng chưa đăng nhập."));

    if (user.getStatus() != ActiveStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Tài khoản chưa được kích hoạt hoặc đang bị khóa.");
    }

    // Chống spam: khóa 5 phút đồng bộ
    if (credentialTokenStore.isRequestLocked(user.getId())) {
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
          "Yêu cầu xác thực trước đó của bạn vẫn đang có hiệu lực. Vui lòng kiểm tra hộp thư hoặc thử lại sau.");
    }

    String rawToken = UUID.randomUUID().toString();
    credentialTokenStore.issueToken(user.getId(), user.getEmail(), rawToken, Duration.ofMinutes(5));

    String activationUrl = frontendUrl + "/credential-change/activate?token=" + rawToken;
    notificationEventProducer.sendCredentialChangeEmail(user.getEmail(), user.getName(), activationUrl, rawToken);

    log.info("Đã phát hành token xác thực đổi credentials cho user: {}", user.getUsername());
    return Response.ok(String.format("Liên kết xác thực thay đổi thông tin đăng nhập đã được gửi đến %s. Vui lòng kiểm tra hộp thư.", helper.maskEmail(user.getEmail())));
  }

  @Override
  @Transactional
  public Response<String> activateCredentialToken(@NonNull final String token) {
    String trimmedToken = token.trim();
    if (trimmedToken.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Token xác thực không được để trống.");
    }

    // Kích hoạt quyền trên Redis (0 DB Query)
    credentialTokenStore.activateToken(trimmedToken)
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS,
            "Liên kết xác thực không hợp lệ hoặc đã hết hạn."));

    return Response.ok("Kích hoạt quyền đổi thông tin thành công. Bạn có 5 phút để cập nhật.");
  }

  @Override
  public Response<CredentialActiveStatusResponse> getCredentialChangeStatus() {
    User user = securityUtil.getCurrentUser()
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Người dùng chưa đăng nhập."));

    // 100% In-Memory Redis Check (0 DB Query)
    Long remainingSeconds = credentialTokenStore.getActiveRemainingSeconds(user.getId());
    if (remainingSeconds == null || remainingSeconds <= 0) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Phiên xác thực chưa được kích hoạt hoặc đã hết hạn.");
    }

    return Response.ok(CredentialActiveStatusResponse.builder()
        .status("ACTIVE")
        .remainingSeconds(remainingSeconds)
        .expiresAt(LocalDateTime.now().plusSeconds(remainingSeconds))
        .build(), "Phiên đổi thông tin đang hoạt động.");
  }

  @Override
  @Transactional
  public Response<String> updateCredentials(@NonNull final UpdateCredentialsRequest request) {
    User user = securityUtil.getCurrentUser()
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Người dùng chưa đăng nhập."));

    // 1. Fast Guard: Kiểm tra quyền ACTIVE trên Redis (0 DB Query if invalid)
    if (!credentialTokenStore.isUserActive(user.getId())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Bạn chưa xác thực qua email hoặc phiên đổi thông tin đã hết hạn.");
    }

    boolean hasNewUsername = StringUtils.hasText(request.getNewUsername());
    boolean hasNewPassword = StringUtils.hasText(request.getNewPassword());

    if (!hasNewUsername && !hasNewPassword) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED,
          "Vui lòng cung cấp ít nhất Tên đăng nhập mới hoặc Mật khẩu mới cần thay đổi.");
    }

    // 2. Xử lý đổi Username nếu có
    if (hasNewUsername) {
      String newUsername = request.getNewUsername().trim();
      if (newUsername.contains("@")) {
        throw new BusinessException(ErrorCode.INVALID_FORMAT, "Tên đăng nhập không được chứa ký tự '@'.");
      }
      if (redisService.hasKey(RedisTable.AUTH_GUARD_COOLDOWN, user.getId())) {
        throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
            "Bạn chỉ được đổi tên đăng nhập tối đa 1 lần mỗi tháng. Vui lòng quay lại sau.");
      }
      if (userRepository.findByName(newUsername).isPresent()) {
        throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
            "Tên đăng nhập mới đã tồn tại trên hệ thống.");
      }
      user.setName(newUsername);
      redisService.setValueWithExpiry(RedisTable.AUTH_GUARD_COOLDOWN, user.getId(), "true", 30, TimeUnit.DAYS);
    }

    // 3. Xử lý đổi Password nếu có
    if (hasNewPassword) {
      String newPassword = request.getNewPassword();
      String confirmPassword = request.getConfirmPassword();
      if (confirmPassword == null || !newPassword.equals(confirmPassword)) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Mật khẩu xác nhận không trùng khớp.");
      }
      user.setPassword(passwordEncoder.encode(newPassword));
    }

    userRepository.save(user);

    // 4. Single-action: Thu hồi ngay quyền ACTIVE và LOCK trên Redis
    credentialTokenStore.consumeActiveGrant(user.getId());

    // 5. Thu hồi mọi phiên đăng nhập cũ
    refreshTokenService.revokeAllUserTokens(user.getId());
    log.info("Cập nhật credentials thành công cho user: {}", user.getUsername());

    return Response.ok("Cập nhật thông tin đăng nhập thành công. Vui lòng đăng nhập lại.");
  }
}