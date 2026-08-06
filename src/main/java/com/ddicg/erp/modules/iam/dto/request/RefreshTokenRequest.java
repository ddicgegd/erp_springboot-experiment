package com.ddicg.erp.modules.iam.dto.request;

import com.ddicg.erp.core.common.model.embedded.DeviceInfo;

public class RefreshTokenRequest {
    private String refreshToken;
    private DeviceInfo deviceInfo;

    public RefreshTokenRequest() {}

    public RefreshTokenRequest(String refreshToken, DeviceInfo deviceInfo) {
        this.refreshToken = refreshToken;
        this.deviceInfo = deviceInfo;
    }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public DeviceInfo getDeviceInfo() { return deviceInfo; }
    public void setDeviceInfo(DeviceInfo deviceInfo) { this.deviceInfo = deviceInfo; }
}
