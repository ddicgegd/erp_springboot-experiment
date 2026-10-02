# Ticket 02: Thiết kế Interface Tokenization Gateway và Google Pay Web Vaulting Service

- Type: `wayfinder:prototype`
- Status: `open`
- Blocked by: `[01-cleanup-google-play-and-refactor-schema]`
- Assignee: `unassigned`

## Question
Đặc tả luồng xử lý nhận Google Pay Web payload (`paymentData` token), giải mã và lưu thẻ vào `CustomerPaymentProfile` của User qua `PaymentGatewayClient` (Generic/Internal Gateway với Mock) như thế nào để đảm bảo tính module hóa và không phụ thuộc cứng vào bên thứ ba?

## Acceptance Criteria
- Xây dựng `PaymentGatewayClient` interface hỗ trợ: `vaultPaymentMethod(userId, googlePayToken)` và `chargeRecurringMit(profileId, amount, currency)`.
- Triển khai `MockPaymentGatewayClient` mô phỏng sinh `multiUseToken` và giao dịch trừ tiền MIT.
- Cung cấp `GooglePayWebService` nhận token từ Web client, lưu thẻ vào tài khoản người dùng, kích hoạt gói Trial 30 ngày (0 VND), nâng hạng `UserRank`.
