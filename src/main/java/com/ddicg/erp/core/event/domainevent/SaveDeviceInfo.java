package com.ddicg.erp.core.event.domainevent;

import com.ddicg.erp.core.common.model.embedded.DeviceInfo;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import lombok.Builder;

@Builder
public record SaveDeviceInfo(User userInfo, DeviceInfo deviceInfo, ActiveStatus purpose) {}
