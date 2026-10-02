# Wayfinder Map: Google Pay API for Web Subscription & Fineract Accounting Replacement

## Destination
Hệ thống thanh toán gói định kỳ hoàn chỉnh trên Web: Loại bỏ Google Play In-App; tích hợp Google Pay API for Web (Card Vaulting / Tokenization) xử lý gói 3/6/12 tháng (kèm 30 ngày Free Trial), quản lý vòng đời thanh toán định kỳ MIT; liên kết thẻ/vault trực tiếp trên tài khoản người dùng; và tự động hạch toán doanh thu tức thì vào sổ cái Fineract (GL Journal Entry) ngay khi giao dịch thanh toán thành công (Done).

## Notes
- Domain: Subscriptions, Web Payments (Google Pay Web + Gateway Vaulting), Core Banking Accounting (Apache Fineract).
- Standing Preferences:
  + Lưu vaulted token và phương thức thanh toán gắn chặt với tài khoản người dùng (`User` / `customer_payment_profiles`).
  + Gói Free Trial (0 VND) không ghi nhận doanh thu Fineract; chỉ ghi nhận khi thanh toán trừ tiền thành công (Done).
  + Không dùng 3 backticks trong phản hồi terminal.
  + Tuân thủ TDD cho các seams công khai và bảo toàn tính toàn vẹn của nghiệp vụ UserRank.

## Decisions so far

<!-- the index: one line per closed ticket, enough to judge relevance, then zoom the link for the detail the ticket holds -->

## Not yet specified

<!-- see "Fog of war": in-scope fog you can't ticket yet; graduates as the frontier advances -->
- [Fineract Account Mapping Specialization]: Cần tài khoản kế toán riêng cho Doanh thu dịch vụ Subscription thay vì dùng chung `salesRevenueGlAccountId` của đơn hàng vật lý?
- [Webhook Token Lifecycle Management (TLM)]: Tiếp nhận thông báo khi thẻ của người dùng bị khóa/hết hạn từ Google Pay Web / Gateway để vô hiệu hóa profile trên tài khoản.
- [GraphQL Gateway Schema Adaptation]: Định nghĩa lại mutation `initiateGooglePaySubscription` và `chargeSubscription` tại `graphQL-service`.

## Out of scope

<!-- see "Out of scope": work ruled beyond the destination; closed, never graduates -->
- [Google Play In-App Billing Android SDK]: Bị loại bỏ hoàn toàn khỏi kiến trúc; không hỗ trợ In-App billing native trên Android.
- [Dynamic Pricing for MIT]: Quy chuẩn Google Pay MIT không cho phép đổi giá tùy ý giữa các chu kỳ định kỳ.
