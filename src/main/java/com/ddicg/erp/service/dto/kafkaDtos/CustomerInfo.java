package com.ddicg.erp.service.dto.kafkaDtos;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomerInfo {
    String ipAddress;         // IP khách hàng (Bắt buộc với VNPay: vnp_IpAddr)
    String appUserId;         // ID định danh user trên hệ thống của bạn (Bắt buộc với ZaloPay: app_user)
    String language;          // Ngôn ngữ hiển thị cổng thanh toán (VD: "vn", "en")
}
