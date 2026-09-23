package com.ddicg.erp.modules.notification.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Payload gói tin truyền qua Apache Kafka để yêu cầu gửi email bất đồng bộ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailDispatchPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Mã định danh duy nhất của gói tin (UUID).
     */
    private String messageId;

    /**
     * Địa chỉ email người nhận.
     */
    private String recipient;

    /**
     * Tiêu đề email (nếu để trống sẽ dùng defaultSubject của template).
     */
    private String subject;

    /**
     * Mã template (ví dụ: VERIFICATION_EMAIL, ACCOUNT_RECOVERY).
     */
    private String templateCode;

    /**
     * Các biến truyền vào template (key-value phẳng để tránh rủi ro serialization).
     */
    @Builder.Default
    private Map<String, String> params = new HashMap<>();

    /**
     * Khóa chống gửi lặp (Idempotency key) dùng với Redis.
     */
    private String deduplicationKey;

    /**
     * Mức độ ưu tiên (1: Cao - OTP/Bảo mật, 2: Bình thường, 3: Marketing/Báo cáo).
     */
    @Builder.Default
    private int priority = 1;

    /**
     * Thời điểm phát sinh yêu cầu gửi.
     */
    @Builder.Default
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime requestedAt = LocalDateTime.now();
}
