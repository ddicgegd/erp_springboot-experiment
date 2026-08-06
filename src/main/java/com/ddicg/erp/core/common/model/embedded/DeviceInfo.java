package com.ddicg.erp.core.common.model.embedded;

import jakarta.persistence.Embeddable;

@Embeddable
public class DeviceInfo {
    private String deviceType;
    private String deviceName;
    private String osName;
    private String ipAddress;

    public DeviceInfo() {}

    public DeviceInfo(String deviceType, String deviceName, String osName, String ipAddress) {
        this.deviceType = deviceType;
        this.deviceName = deviceName;
        this.osName = osName;
        this.ipAddress = ipAddress;
    }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getOsName() { return osName; }
    public void setOsName(String osName) { this.osName = osName; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
