package com.ddicg.erp.modules.notification.service;

import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.modules.notification.dto.TemplateCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTemplateServiceImpl implements EmailTemplateService {

    private final SpringTemplateEngine templateEngine;

    @Override
    public String renderHtml(String templateCode, Map<String, String> variables) {
        String templatePath = resolveTemplatePath(templateCode);

        Context context = new Context();
        if (variables != null) {
            variables.forEach(context::setVariable);
        }

        try {
            return templateEngine.process(templatePath, context);
        } catch (Exception e) {
            log.error("[EmailTemplateService] Lỗi khi render template '{}': {}", templatePath, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể render nội dung email: " + e.getMessage());
        }
    }

    @Override
    public String resolveDefaultSubject(String templateCode) {
        TemplateCode code = TemplateCode.fromCode(templateCode);
        return code != null ? code.getDefaultSubject() : "Thông báo từ hệ thống ERP";
    }

    private String resolveTemplatePath(String templateCode) {
        TemplateCode code = TemplateCode.fromCode(templateCode);
        String name;
        if (code != null) {
            name = code.getTemplateFile();
        } else if (templateCode != null && !templateCode.isBlank()) {
            name = templateCode.trim();
        } else {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Mã template email không hợp lệ");
        }

        if (name.endsWith(".html")) {
            name = name.substring(0, name.length() - 5);
        }
        if (!name.startsWith("mail/") && !name.contains("/")) {
            name = "mail/" + name;
        }
        return name;
    }
}
