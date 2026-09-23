package com.ddicg.erp.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kết quả thực thi gửi email trong pipeline xử lý.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDeliveryResult {

    public enum Status {
        SUCCESS,
        DROPPED_DUPLICATE,
        DROPPED_RATE_LIMITED,
        FAILED
    }

    private String messageId;
    private String recipient;
    private Status status;
    private String detail;
    private LocalDateTime timestamp;

    public static EmailDeliveryResult success(String messageId, String recipient) {
        return EmailDeliveryResult.builder()
                .messageId(messageId)
                .recipient(recipient)
                .status(Status.SUCCESS)
                .detail("Gửi email thành công")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static EmailDeliveryResult duplicate(String messageId, String recipient, String dedupKey) {
        return EmailDeliveryResult.builder()
                .messageId(messageId)
                .recipient(recipient)
                .status(Status.DROPPED_DUPLICATE)
                .detail("Bỏ qua do phát hiện email trùng lặp (Dedup Key: " + dedupKey + ")")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static EmailDeliveryResult rateLimited(String messageId, String recipient) {
        return EmailDeliveryResult.builder()
                .messageId(messageId)
                .recipient(recipient)
                .status(Status.DROPPED_RATE_LIMITED)
                .detail("Bỏ qua do vượt quá giới hạn tần suất gửi email tới: " + recipient)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static EmailDeliveryResult failed(String messageId, String recipient, String reason) {
        return EmailDeliveryResult.builder()
                .messageId(messageId)
                .recipient(recipient)
                .status(Status.FAILED)
                .detail(reason)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
