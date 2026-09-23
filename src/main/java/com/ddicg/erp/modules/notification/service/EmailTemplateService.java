package com.ddicg.erp.modules.notification.service;

import com.ddicg.erp.modules.notification.dto.TemplateCode;

import java.util.Map;

/**
 * Service phân giải và biên dịch các mẫu HTML email bằng Thymeleaf template engine.
 */
public interface EmailTemplateService {

    /**
     * Render template HTML với các biến động.
     *
     * @param templateCode Mã template định danh (ví dụ: VERIFICATION_EMAIL) hoặc tên file template
     * @param variables Danh sách biến truyền vào template
     * @return Chuỗi HTML hoàn chỉnh sau khi render
     */
    String renderHtml(String templateCode, Map<String, String> variables);

    /**
     * Lấy tiêu đề mặc định của template.
     *
     * @param templateCode Mã template định danh
     * @return Tiêu đề email mặc định
     */
    String resolveDefaultSubject(String templateCode);
}
