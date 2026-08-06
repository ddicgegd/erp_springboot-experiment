package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.model.embedded.DeviceInfo;
import org.springframework.stereotype.Service;

@Service
public class DeviceInfoService {

    /**
     * Tạo deviceId từ thông tin thiết bị (ví dụ: windows:desktop).
     *
     * @param deviceInfo thông tin thiết bị
     * @return chuỗi định danh duy nhất cho thiết bị
     */
    public String createDeviceId(DeviceInfo deviceInfo) {
        if (deviceInfo == null) {
            return "unknown_device";
        }
        String os = deviceInfo.getOsName() != null ? deviceInfo.getOsName().trim().toLowerCase() : "unknown_os";
        String type = deviceInfo.getDeviceType() != null ? deviceInfo.getDeviceType().trim().toLowerCase() : "unknown_type";
        return os + ":" + type;
    }
}
