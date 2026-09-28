package com.ddicg.erp.modules.notification.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Danh mục mã template email hỗ trợ trong hệ thống.
 */
@Getter
@RequiredArgsConstructor
public enum TemplateCode {

    VERIFICATION_EMAIL("mail/verification-email", "Xác thực tài khoản"),
    ACCOUNT_RECOVERY("mail/account-recovery-email", "Khôi phục thông tin tài khoản"),
    BIRTHDAY_GREETING("mail/birthday-email", "Chúc mừng sinh nhật quý khách!"),
    CREDENTIAL_CHANGE("mail/credential-change-email", "Xác thực thay đổi thông tin đăng nhập");

    private final String templateFile;
    private final String defaultSubject;

    public static TemplateCode fromCode(String code) {
        if (code == null) return null;
        for (TemplateCode tc : values()) {
            if (tc.name().equalsIgnoreCase(code)) {
                return tc;
            }
        }
        return null;
    }
}
