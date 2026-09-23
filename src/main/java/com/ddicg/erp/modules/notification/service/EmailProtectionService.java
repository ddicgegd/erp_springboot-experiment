package com.ddicg.erp.modules.notification.service;

/**
 * Service bảo vệ luồng gửi email qua Redis:
 * 1. Chống gửi trùng lặp (Idempotency / Deduplication) khi Kafka retry hoặc trùng event.
 * 2. Giới hạn tần suất gửi (Rate Limiting) trên từng địa chỉ email người nhận.
 */
public interface EmailProtectionService {

    /**
     * Kiểm tra và khóa deduplication key.
     *
     * @param dedupKey Khóa định danh tác vụ gửi mail (nếu null/rỗng coi như bỏ qua)
     * @param ttlMinutes Thời gian tồn tại của khóa (phút)
     * @return true nếu khóa hợp lệ và lần đầu được ghi (được phép gửi); false nếu phát hiện trùng lặp
     */
    boolean acquireDeduplicationLock(String dedupKey, long ttlMinutes);

    /**
     * Kiểm tra giới hạn tần suất gửi email tới 1 địa chỉ.
     *
     * @param recipient Địa chỉ email người nhận
     * @param maxPerMinute Số lượng email tối đa trong 1 phút
     * @return true nếu trong ngưỡng cho phép; false nếu vượt quá rate limit
     */
    boolean allowDeliveryRate(String recipient, int maxPerMinute);
}
