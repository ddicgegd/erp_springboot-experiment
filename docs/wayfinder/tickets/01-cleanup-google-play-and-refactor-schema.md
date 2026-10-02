# Ticket 01: Dọn dẹp Google Play In-App và Tái cấu trúc Schema Lưu trữ Tài khoản Người dùng

- Type: `wayfinder:task`
- Status: `open`
- Blocked by: `[]`
- Assignee: `omp-agent`

## Question
Làm thế nào để dọn dẹp sạch sẽ các thành phần thừa của Google Play In-App (RTDN Webhook, Google Play Client, Verification Service) và tái cấu trúc bảng `user_subscriptions`, `customer_payment_profiles` để lưu thông tin thẻ/vault trực tiếp trên tài khoản người dùng (`userId`) đáp ứng yêu cầu Google Pay for Web?

## Acceptance Criteria
- Xóa bỏ các file liên quan độc quyền đến Google Play Android SDK (`GooglePlayVerificationService`, `GoogleRtdnHandlerService`, `GooglePlayWebhookController`, `GooglePlaySubscriptionClient`).
- Cập nhật Flyway V20 (hoặc tạo V21) thiết lập bảng `customer_payment_profiles` liên kết với `user_id`, lưu trữ `multi_use_token`, `card_network`, `fpan_last_four`, `status`.
- Điều chỉnh bảng `user_subscriptions` liên kết với `payment_profile_id` thay vì `purchase_token` dạng Android.
