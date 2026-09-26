# ADR-002: Token-Based Credential Change Architecture with 5-Minute In-Memory Redis Session

## Status
Accepted

## Date
2026-09-26

## Context
Trong hệ thống ERP, việc đổi các thông tin định danh nhạy cảm như Tên đăng nhập (`username`) và Mật khẩu (`password`) đòi hỏi cơ chế xác thực hai lớp (Two-Factor Authorization qua Email) để ngăn ngừa hành vi chiếm quyền tài khoản khi phiên đăng nhập bị lộ hoặc thiết bị bị bỏ quên.

Yêu cầu nghiệp vụ:
1. Người dùng cần kích hoạt mã xác thực gửi về email trước khi được phép cập nhật `username`/`password`.
2. Sau khi kích hoạt thành công, hệ thống mở một cửa sổ cấp quyền tạm thời đúng **5 phút** (`TTL = 5m = 300s`) lưu trên Redis.
3. Người dùng được phép đổi `username`, `password`, hoặc **cả hai cùng lúc trong 1 token duy nhất**.
4. Khóa cấp quyền trên Redis phải có định dạng rõ ràng và hỗ trợ kiểm tra tồn tại trực tiếp trên bộ nhớ đệm (In-Memory Select Exists) để **tránh 100% việc truy vấn trực tiếp vào Cơ sở dữ liệu** khi token không hợp lệ hoặc đã hết hạn.
5. Vẫn duy trì bất biến kiểm tra Cooldown 30 ngày đối với `username`.

## Decision

1. **Kiến trúc Token 2 Giai đoạn (2-Stage Token Lifecycle):**
   - **Giai đoạn 1 (Pending Token - 15 phút):** Khi người dùng yêu cầu, token UUID được sinh và băm SHA-256 lưu vào `AUTH_CREDENTIAL_TOKEN` (`auth:action:credential:token:{tokenHash}` $\rightarrow$ `email`). Email gửi liên kết kích hoạt đến người dùng kèm mã token.
   - **Giai đoạn 2 (Active Grant Session - 5 phút):** Khi người dùng click liên kết kích hoạt (`GET /api/auth/credential-change/validate?token={token}`), hệ thống băm token và chuyển sang bảng `AUTH_CREDENTIAL_ACTIVE` (`auth:action:credential:active:{tokenHash}` $\rightarrow$ `{userId}:{email}`) với TTL đúng **5 phút** (300 giây).

2. **Cơ chế Kiểm tra In-Memory Fast Guard (Zero Database Query on Failure):**
   - Mọi thao tác kiểm tra trạng thái token hoặc cập nhật thông tin đều thực hiện truy vấn trực tiếp trên Redis (`redisService.getValue` / `hasKey`).
   - Nếu key không tồn tại hoặc đã hết hạn 5 phút $\rightarrow$ trả về `null` hoặc ném `INVALID_CREDENTIALS` ngay lập tức mà **không gọi bất kỳ câu lệnh SQL nào vào CSDL**.

3. **Unified Credential Update Endpoint:**
   - Cung cấp endpoint `PUT /api/auth/update-credentials` nhận `UpdateCredentialsRequest` (`token`, `newUsername`, `newPassword`, `confirmPassword`).
   - Cho phép người dùng gửi chỉ `newUsername`, chỉ `newPassword`, hoặc **cả hai cùng lúc** trong 1 request.
   - Token chỉ sử dụng được 1 lần (Single-use): Sau khi cập nhật thành công, token 5 phút bị hủy ngay lập tức trên Redis và toàn bộ Access/Refresh Token cũ trên mọi thiết bị bị thu hồi (`revokeAllUserTokens`).

## Consequences

- **Bảo mật tối đa:** Ngăn chặn triệt để tấn công Replay Attack và Session Hijacking khi đổi thông tin tài khoản.
- **Tối ưu hiệu năng:** Kiểm tra tính hợp lệ của token hoàn toàn trên In-Memory Redis, giảm tải tối đa cho CSDL Oracle.
- **Trải nghiệm người dùng tốt:** Cho phép đổi cả username và mật khẩu trong 1 lần kích hoạt duy nhất thay vì phải gửi 2 email riêng biệt.

## Backtest & Security Fix

**Ngày thực thi backtest:** 2026-09-27

Sau khi triển khai và kiểm thử đơn vị 100% pass, một buổi backtest trực tiếp trên endpoint thật đã được thực thi với 17 bước xuyên suốt toàn bộ luồng nghiệp vụ. Kết quả phát hiện và vá ngay 1 lỗ hổng cấu hình bảo mật:

### Bug Phát Hiện & Vá (commit `c6be9bb`)

**Vấn đề:** `GET /api/auth/credential-change/activate` trả về `403 Forbidden` khi không có Bearer JWT, mặc dù endpoint này được thiết kế là **Public** (kích hoạt cross-device từ link email).

**Nguyên nhân:** `/api/auth/credential-change/activate` bị thiếu trong mảng `PUBLIC_AUTH_ENDPOINTS` của `SecurityConfiguration.java`. Spring Security chặn request trước khi vào controller.

**Hệ quả thiết kế:** Mọi endpoint Public mới trong phân hệ IAM **phải được khai báo tường minh** trong `PUBLIC_AUTH_ENDPOINTS` của `SecurityConfiguration.java`. Security filter chain deny-by-default — không có exception tự động cho endpoint bất kỳ.

**Fix:** Thêm `"/api/auth/credential-change/activate"` vào mảng `PUBLIC_AUTH_ENDPOINTS` trong `SecurityConfiguration.java`.

### Pre-existing Bug Được Ghi Nhận (out of scope)

`POST /api/auth/refresh-token` trả về `500 Internal Server Error` khi body chứa JWT có cú pháp sai (malformed). `MalformedJwtException` từ tầng Service không được `GlobalExceptionHandler` bắt — nên trả `401` thay vì `500`. Không liên quan đến feature này.

### Redis Serializer Gotcha

`RedisService` sử dụng `GenericJackson2JsonRedisSerializer`. Mọi giá trị lưu Redis phải là **JSON-encoded string** (ví dụ: `"\"ACTIVE\""`, `"\"email@x.com\""`). Seed trực tiếp plain text qua `redis-cli` gây `SerializationException` khi đọc lại. Integration test và script seed phải serialize JSON trước khi ghi.
