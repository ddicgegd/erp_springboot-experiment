package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.model.embedded.DeviceInfo;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.dto.response.DeviceInfoResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RefreshTokenService {

    private final com.ddicg.erp.core.common.service.RedisService redisService;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Value("${application.security.jwt.access-token-expiration-minutes:1440}")
    private long accessTokenExpirationMinutes;

    @Value("${application.security.jwt.refresh-token-expiration-days:90}")
    private long refreshTokenExpirationDays;

    public RefreshTokenService(
            com.ddicg.erp.core.common.service.RedisService redisService,
            JwtService jwtService,
            ObjectMapper objectMapper) {
        this.redisService = redisService;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    /**
     * Xử lý kiểm tra và tạo Token khi Đăng nhập thành công.
     */
    public DeviceInfoResponse handleLoginTokens(User user, UserDetails userDetails, DeviceInfo deviceInfo, String deviceId) {
        String profileKey = "user:" + user.getId() + ":profile";
        String refreshTokenKey = "user:refresh_tokens:" + user.getId();

        // 1. Kiểm tra xem đã có accessToken trong profile chưa và có hợp lệ không
        String existingAccessToken = (String) redisService.hGet(profileKey, "accessToken");
        boolean accessTokenExists = false;
        if (existingAccessToken != null) {
            try {
                accessTokenExists = jwtService.isTokenValid(existingAccessToken, userDetails);
            } catch (Exception e) {
                accessTokenExists = false;
            }
        }

        // 2. Kiểm tra xem đã có refreshToken cho thiết bị này chưa
        Object existingTokenDataObj = redisService.hGet(refreshTokenKey, deviceId);
        boolean refreshTokenExists = false;
        String existingRefreshToken = null;
        if (existingTokenDataObj != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> tokenData = objectMapper.convertValue(
                        existingTokenDataObj, new TypeReference<Map<String, Object>>() {});
                existingRefreshToken = (String) tokenData.get("token");
                if (existingRefreshToken != null) {
                    refreshTokenExists = jwtService.isTokenValid(existingRefreshToken, userDetails);
                }
            } catch (Exception e) {
                refreshTokenExists = false;
            }
        }

        String finalAccessToken;
        String finalRefreshToken;
        String finalMessage;

        // 3. Nếu đã tồn tại hết -> thực hiện refresh theo refreshToken (kèm check hạn)
        if (accessTokenExists && refreshTokenExists) {
            log.info("Session và Refresh Token hợp lệ đã tồn tại cho user: {}, thiết bị: {}. Thực hiện xoay vòng token.", user.getUsername(), deviceId);
            long accessTokenExpiryMs = TimeUnit.MINUTES.toMillis(accessTokenExpirationMinutes);
            finalAccessToken = jwtService.generateToken(userDetails, accessTokenExpiryMs);

            long refreshTokenExpiryMs = TimeUnit.DAYS.toMillis(refreshTokenExpirationDays);
            finalRefreshToken = jwtService.generateToken(userDetails, refreshTokenExpiryMs);

            // Cập nhật profile
            redisService.hSet(profileKey, "accessToken", finalAccessToken);
            redisService.expire(profileKey, accessTokenExpirationMinutes, TimeUnit.MINUTES);

            // Cập nhật refresh token
            Map<String, Object> refreshTokenData = new HashMap<>();
            refreshTokenData.put("token", finalRefreshToken);
            refreshTokenData.put("deviceInfo", deviceInfo);
            redisService.hSet(refreshTokenKey, deviceId, refreshTokenData);
            redisService.expire(refreshTokenKey, refreshTokenExpirationDays, TimeUnit.DAYS);
            finalMessage = "Đăng nhập thành công (Đã làm mới phiên hoạt động).";
        } else {
            // 4. Nếu chưa tồn tại đầy đủ -> tạo mới cả 2
            log.info("Chưa tồn tại đủ session/refresh token cho user: {}, thiết bị: {}. Tạo mới toàn bộ.", user.getUsername(), deviceId);
            long accessTokenExpiryMs = TimeUnit.MINUTES.toMillis(accessTokenExpirationMinutes);
            finalAccessToken = jwtService.generateToken(userDetails, accessTokenExpiryMs);

            long refreshTokenExpiryMs = TimeUnit.DAYS.toMillis(refreshTokenExpirationDays);
            finalRefreshToken = jwtService.generateToken(userDetails, refreshTokenExpiryMs);

            // Lưu profile
            redisService.hSet(profileKey, "accessToken", finalAccessToken);
            redisService.expire(profileKey, accessTokenExpirationMinutes, TimeUnit.MINUTES);

            // Lưu refresh token
            Map<String, Object> refreshTokenData = new HashMap<>();
            refreshTokenData.put("token", finalRefreshToken);
            refreshTokenData.put("deviceInfo", deviceInfo);
            redisService.hSet(refreshTokenKey, deviceId, refreshTokenData);
            redisService.expire(refreshTokenKey, refreshTokenExpirationDays, TimeUnit.DAYS);
            finalMessage = "Đăng nhập thành công.";
        }

        return DeviceInfoResponse.builder()
                .accessToken(finalAccessToken)
                .finalRefreshTokenString(finalRefreshToken)
                .message(finalMessage)
                .build();
    }

    /**
     * Xử lý tạo mới Token khi gọi luồng Refresh Token.
     */
    public DeviceInfoResponse refreshSessionTokens(User user, UserDetails userDetails, DeviceInfo deviceInfo, String newDeviceId, String oldDeviceId) {
        String profileKey = "user:" + user.getId() + ":profile";
        String refreshTokenKey = "user:refresh_tokens:" + user.getId();

        long accessTokenExpiryMs = TimeUnit.MINUTES.toMillis(accessTokenExpirationMinutes);
        String finalAccessToken = jwtService.generateToken(userDetails, accessTokenExpiryMs);

        long refreshTokenExpiryMs = TimeUnit.DAYS.toMillis(refreshTokenExpirationDays);
        String finalRefreshToken = jwtService.generateToken(userDetails, refreshTokenExpiryMs);

        // Thu hồi token cũ của thiết bị cũ nếu thiết bị thay đổi (rotate)
        if (oldDeviceId != null && !oldDeviceId.equals(newDeviceId)) {
            redisService.hDelete(refreshTokenKey, oldDeviceId);
            log.info("Thu hồi Refresh Token của thiết bị cũ: {}", oldDeviceId);
        }

        // Cập nhật profile (accessToken)
        redisService.hSet(profileKey, "accessToken", finalAccessToken);
        redisService.expire(profileKey, accessTokenExpirationMinutes, TimeUnit.MINUTES);

        // Cập nhật refresh token cho thiết bị mới
        Map<String, Object> refreshTokenData = new HashMap<>();
        refreshTokenData.put("token", finalRefreshToken);
        refreshTokenData.put("deviceInfo", deviceInfo);
        redisService.hSet(refreshTokenKey, newDeviceId, refreshTokenData);
        redisService.expire(refreshTokenKey, refreshTokenExpirationDays, TimeUnit.DAYS);

        return DeviceInfoResponse.builder()
                .accessToken(finalAccessToken)
                .finalRefreshTokenString(finalRefreshToken)
                .message("Tạo mới token thành công.")
                .build();
    }

    /**
     * Thu hồi toàn bộ token và session khi logout.
     */
    public void revokeAllUserTokens(Long userId) {
        String profileKey = "user:" + userId + ":profile";
        String refreshTokenKey = "user:refresh_tokens:" + userId;
        redisService.delete(profileKey, refreshTokenKey);
        log.info("Thu hồi toàn bộ session và refresh token của user ID: {}", userId);
    }
}