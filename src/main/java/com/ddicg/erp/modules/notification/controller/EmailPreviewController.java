package com.ddicg.erp.modules.notification.controller;

import com.ddicg.erp.modules.notification.dto.EmailPreviewRequest;
import com.ddicg.erp.modules.notification.dto.TemplateCode;
import com.ddicg.erp.modules.notification.service.EmailTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller nội bộ phục vụ xem trước (Preview) giao diện HTML của các mẫu email.
 */
@RestController
@RequestMapping("/api/internal/notification/preview")
@RequiredArgsConstructor
@Tag(name = "Notification Preview", description = "Endpoints nội bộ xem trước giao diện email HTML")
public class EmailPreviewController {

    private final EmailTemplateService emailTemplateService;

    @GetMapping(value = "/{templateCode}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @Operation(summary = "Xem trước email HTML mẫu theo mã template trên trình duyệt")
    public ResponseEntity<String> previewByGet(
            @PathVariable String templateCode,
            @RequestParam(required = false, defaultValue = "Nguyễn Văn A") String username,
            @RequestParam(required = false, defaultValue = "http://localhost:3000/auth/action-demo?token=PREVIEW_123456") String url
    ) {
        Map<String, String> vars = new HashMap<>();
        vars.put("username", username);
        vars.put("subject", emailTemplateService.resolveDefaultSubject(templateCode));

        TemplateCode code = TemplateCode.fromCode(templateCode);
        String token = "PREVIEW_123456";
        if (url != null && url.contains("token=")) {
            token = url.substring(url.indexOf("token=") + 6);
        }
        vars.put("token", token);

        if (code == TemplateCode.ACCOUNT_RECOVERY) {
            vars.put("resetUrl", url);
        } else {
            vars.put("verificationUrl", url);
        }

        String renderedHtml = emailTemplateService.renderHtml(templateCode, vars);
        return ResponseEntity.ok(renderedHtml);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @Operation(summary = "Xem trước email HTML với biến tùy chỉnh (POST JSON)")
    public ResponseEntity<String> previewByPost(@Valid @RequestBody EmailPreviewRequest request) {
        String renderedHtml = emailTemplateService.renderHtml(request.getTemplateCode(), request.getVariables());
        return ResponseEntity.ok(renderedHtml);
    }
}
