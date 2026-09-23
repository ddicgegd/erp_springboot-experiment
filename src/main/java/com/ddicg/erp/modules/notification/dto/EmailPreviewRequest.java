package com.ddicg.erp.modules.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO yêu cầu preview template HTML trên giao diện/trình duyệt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailPreviewRequest {

    @NotBlank(message = "Template code không được để trống")
    private String templateCode;

    @Builder.Default
    private Map<String, String> variables = new HashMap<>();
}
