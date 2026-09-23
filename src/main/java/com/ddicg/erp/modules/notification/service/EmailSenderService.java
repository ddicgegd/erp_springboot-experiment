package com.ddicg.erp.modules.notification.service;

import jakarta.mail.MessagingException;

/**
 * Service cấp thấp thực hiện gửi MIME message qua giao thức SMTP.
 */
public interface EmailSenderService {

    /**
     * Gửi email định dạng HTML.
     *
     * @param to Địa chỉ email người nhận
     * @param subject Tiêu đề email
     * @param htmlContent Nội dung HTML
     * @throws MessagingException khi kết nối hoặc gửi SMTP thất bại
     */
    void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException;
}
