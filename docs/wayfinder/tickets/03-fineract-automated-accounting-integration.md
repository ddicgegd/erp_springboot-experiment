# Ticket 03: Tích hợp Tự động Hạch toán Fineract khi Xử lý Thành công (Done)

- Type: `wayfinder:grilling`
- Status: `open`
- Blocked by: `[02-tokenization-gateway-and-google-pay-service]`
- Assignee: `unassigned`

## Question
Khi giao dịch trừ tiền định kỳ (MIT) hoặc thanh toán kích hoạt có phí hoàn tất thành công (`Done`), quy trình tích hợp với `FineractJournalService` sẽ được kích hoạt như thế nào để đảm bảo tính toàn vẹn (Nợ TK Ngân hàng / Có TK Doanh thu dịch vụ), xử lý retry nếu Fineract bận, và đảm bảo gói Trial (0 VND) không sinh bút toán giả?

## Acceptance Criteria
- Xây dựng service điều phối thanh toán định kỳ (`SubscriptionBillingService`).
- Khi charge thành công gói 3/6/12 tháng:
  + Cập nhật trạng thái `UserSubscription` sang `ACTIVE`.
  + Gia hạn `expiry_time` theo số tháng tương ứng.
  + Gọi `FineractJournalService.recordSale(...)` với `PaymentMethod.GOOGLE_PAY` (hoặc Bank asset GL), ghi chú rõ mã đăng ký gói và User ID.
- Xử lý lỗi hạch toán không làm rollback giao dịch trừ tiền đã thành công nhưng ghi log cảnh báo và đánh dấu audit log.
