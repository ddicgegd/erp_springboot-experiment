package com.ddicg.erp.core.common.util;

import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;

import java.text.Normalizer;

/**
 * Utility bóc tách và chuẩn hóa họ tên tiếng Việt theo chuẩn nghiệp vụ ngân hàng/Core Banking.
 * Quy ước Fineract:
 * - firstname: Tên chính (Given name)
 * - lastname: Họ và tên đệm (Family & Middle name)
 */
public final class VietnameseNameParser {

    private VietnameseNameParser() {
        // Utility class
    }

    public record ParsedName(String firstname, String lastname, String fullName) {}

    public static ParsedName parse(String rawFullName) {
        if (rawFullName == null || rawFullName.isBlank()) {
            throw new BusinessException(ErrorCode.USER_PROFILE_INCOMPLETE, "Họ và tên người dùng không được để trống khi liên kết hệ thống tài chính");
        }

        // Chuẩn hóa Unicode NFC và làm sạch khoảng trắng thừa
        String normalized = Normalizer.normalize(rawFullName, Normalizer.Form.NFC).trim();
        String[] parts = normalized.split("\\s+");

        if (parts.length == 0 || (parts.length == 1 && parts[0].isBlank())) {
            throw new BusinessException(ErrorCode.USER_PROFILE_INCOMPLETE, "Họ và tên người dùng không hợp lệ");
        }

        String cleanedFullName = String.join(" ", parts);

        if (parts.length == 1) {
            // Tên chỉ có 1 từ (ví dụ: "Định")
            return new ParsedName(parts[0], parts[0], cleanedFullName);
        }

        // Từ cuối cùng là tên chính (Given name -> firstname trong Fineract)
        String firstname = parts[parts.length - 1];
        // Phần còn lại phía trước là họ và đệm (Family/Middle name -> lastname trong Fineract)
        String lastname = cleanedFullName.substring(0, cleanedFullName.length() - firstname.length()).trim();

        return new ParsedName(firstname, lastname, cleanedFullName);
    }
}
