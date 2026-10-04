package com.ddicg.erp.modules.iam.service;

public interface AuthTicketStore {
    /**
     * Sinh mã ticket tạm thời và lưu vào Redis.
     * @param userId ID của người dùng.
     * @return Mã ticket ngẫu nhiên.
     */
    String createTicket(Long userId);

    /**
     * Đối soát và tiêu thụ (xóa) ticket từ Redis theo cơ chế nguyên tử (Atomic Get-and-Delete).
     * @param ticket Mã ticket do Frontend gửi lên.
     * @return ID của người dùng.
     */
    Long consumeTicket(String ticket);
}
