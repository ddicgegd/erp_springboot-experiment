package com.ddicg.erp.domainevent;

import com.ddicg.erp.model.embedded.DeviceInfo;
import com.ddicg.erp.model.entity.User;
import com.ddicg.erp.model.enums.ActiveStatus;
import lombok.Builder;

@Builder
public record SaveDeviceInfo(User userInfo, DeviceInfo deviceInfo, ActiveStatus purpose) {}
