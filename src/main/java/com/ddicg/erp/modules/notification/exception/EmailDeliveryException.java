package com.ddicg.erp.modules.notification.exception;

/**
 * Ngoại lệ phát sinh khi quá trình gửi email thất bại qua SMTP hoặc xử lý Kafka.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
