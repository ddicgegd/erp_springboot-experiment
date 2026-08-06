package com.ddicg.erp.modules.iam.dto.request;

import com.ddicg.erp.core.common.model.embedded.DeviceInfo;

public class UserLoginRequest {
    private String usernameOrEmail;
    private String password;
    private DeviceInfo deviceInfo;

    public UserLoginRequest() {}

    public UserLoginRequest(String usernameOrEmail, String password, DeviceInfo deviceInfo) {
        this.usernameOrEmail = usernameOrEmail;
        this.password = password;
        this.deviceInfo = deviceInfo;
    }

    public String getUsernameOrEmail() { return usernameOrEmail; }
    public void setUsernameOrEmail(String usernameOrEmail) { this.usernameOrEmail = usernameOrEmail; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public DeviceInfo getDeviceInfo() { return deviceInfo; }
    public void setDeviceInfo(DeviceInfo deviceInfo) { this.deviceInfo = deviceInfo; }
}
