# Ticket 04: Bộ Kiểm thử Seam-Based TDD cho Toàn bộ Luồng Web + Fineract

- Type: `wayfinder:task`
- Status: `open`
- Blocked by: `[03-fineract-automated-accounting-integration]`
- Assignee: `unassigned`

## Question
Làm thế nào để thiết lập bộ kiểm thử nghiệm thu TDD bao quát toàn bộ 4 seams quan trọng: (1) Lưu thẻ trên tài khoản và kích hoạt Trial 30 ngày (không hạch toán Fineract), (2) Gia hạn định kỳ MIT trừ tiền thành công và tự động hạch toán Fineract, (3) Xử lý gián đoạn thanh toán (Grace period / Expire hạ rank), (4) Tính lũy kế idempotency?

## Acceptance Criteria
- Unit & Seam integration tests kiểm tra độc lập các component.
- Mock Fineract RestClient và Gateway Client kiểm tra chính xác số lần gọi và đối số bút toán Journal Entry.
- `./mvnw test` pass 100%.
