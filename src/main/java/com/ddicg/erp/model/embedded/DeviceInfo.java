package com.ddicg.erp.model.embedded;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceInfo {
    @Column(name = "device_type")
    String deviceType;

    @Column(name = "os_name")
    String osName;

    @Column(name = "os_version")
    String osVersion;

    @Column(name = "browser_name")
    String browserName;

    @Column(name = "browser_version")
    String browserVersion;

    @Column(name = "screen_width")
    Integer screenWidth;

    @Column(name = "screen_height")
    Integer screenHeight;

    @Column(name = "user_agent")
    String userAgent;

    @Column(name = "ip_address")
    String ipAddress;

    String language;

    @Column(name = "time_zone")
    String timeZone;

    @Column(name = "device_id")
    String deviceId;

    public DeviceInfo(DeviceInfo other) {
        if (other != null) {
            this.deviceType = other.deviceType;
            this.osName = other.osName;
            this.osVersion = other.osVersion;
            this.browserName = other.browserName;
            this.browserVersion = other.browserVersion;
            this.screenWidth = other.screenWidth;
            this.screenHeight = other.screenHeight;
            this.userAgent = other.userAgent;
            this.ipAddress = other.ipAddress;
            this.language = other.language;
            this.timeZone = other.timeZone;
            this.deviceId = other.deviceId;
        }
    }

}
