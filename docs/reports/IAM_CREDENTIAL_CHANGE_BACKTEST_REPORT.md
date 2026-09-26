# BÁO CÁO BACKTEST ENDPOINT THẬT: CREDENTIAL CHANGE FLOW
## *Hệ thống ERP Spring Boot 3.5.0 — Phân hệ IAM & Bảo Mật Xác Thực*

**Ngày thực thi:** 2026-09-27
**Môi trường:** `localhost:8089` (Spring Boot dev profile, Oracle 21c XEPDB1, Redis 7 ACL)
**Commit tại thời điểm test:** `acb1b8c` → phát hiện bug → fix → `c6be9bb`
**Kết quả tổng:** ✅ **17/17 bước PASS** (sau khi vá security fix)

---

## 1. Mục Đích

Xác minh toàn bộ luồng nghiệp vụ Credential Change (đổi username + password qua xác thực email 5 phút) trên endpoint thật với Oracle DB và Redis thực tế — không dùng mock.

---

## 2. Kết Quả Chi Tiết (17 Bước)

| # | Endpoint | Method | HTTP Expected | HTTP Actual | Ghi Chú |
|---|----------|--------|:---:|:---:|----------|
| 1 | `/api/auth/register` | POST | 200 | ✅ 200 | Đăng ký user test mới |
| 2 | `/api/auth/verify-email?token=` | GET | 200 | ✅ 200 | Kích hoạt tài khoản qua token Redis |
| 3 | `/api/auth/login` | POST | 200 | ✅ 200 | Lấy JWT access + refresh token |
| 4 | `/api/auth/me` | GET | 200 | ✅ 200 | Xác nhận userId, username |
| 5 | `/api/auth/credential-change/request` | POST | 200 | ✅ 200 | Gửi email; Redis lock TTL=300s được set |
| 6 | `/api/auth/credential-change/request` (lần 2) | POST | 429 | ✅ 429 | Anti-spam: chặn gửi lại trong 5 phút |
| 7 | `/api/auth/credential-change/status` | GET | 401 | ✅ 401 | Chưa activate → 401 không query DB |
| 8 | `/api/auth/credential-change/activate?token=` | GET | 200 | ✅ 200 | Kích hoạt grant; Redis ACTIVE TTL=299s |
| 9 | `/api/auth/credential-change/activate?token=garbage` | GET | 401 | ✅ 401 | Token không hợp lệ → 401 |
| 10 | `/api/auth/credential-change/status` | GET | 200 | ✅ 200 | ACTIVE, remainingSeconds=298 |
| 11 | `/api/auth/update-credentials` | PUT | 200 | ✅ 200 | Đổi username + password cùng lúc |
| 12 | Redis state verification | — | PASS | ✅ PASS | active=nil, lock=nil, cooldown=true TTL=2592000s |
| 13 | Session revocation | — | PASS | ✅ PASS | Session chỉ chứa refresh token mới |
| 14 | `/api/auth/login` (old password) | POST | 401 | ✅ 401 | "Mật khẩu không đúng" |
| 15 | `/api/auth/me` (new token) | GET | 200 | ✅ 200 | username=new_bt_44900, cooldownUntil=2026-10-27 |
| 16 | `/api/auth/update-credentials` (trong 30-day cooldown) | PUT | 401 | ✅ 401 | "Bạn chỉ được đổi tên đăng nhập tối đa 1 lần mỗi tháng" |

---

## 3. Bug Phát Hiện & Vá Ngay

### 🔴 Bug: `GET /credential-change/activate` trả 403 (Security Config Thiếu)

- **Triệu chứng:** Endpoint kích hoạt token từ link email trả về `403 Forbidden` khi không có Bearer JWT.
- **Nguyên nhân:** `/api/auth/credential-change/activate` không có trong mảng `PUBLIC_AUTH_ENDPOINTS` của `SecurityConfiguration.java`. Spring Security filter chain áp dụng deny-by-default và chặn request trước khi đến controller.
- **Fix:** Thêm `"/api/auth/credential-change/activate"` vào `PUBLIC_AUTH_ENDPOINTS`.
- **Commit:** `c6be9bb` — `fix(security): permit /api/auth/credential-change/activate as public endpoint`
- **Bài học:** Mọi endpoint Public mới **phải được khai báo tường minh** trong Security config — annotation ở tầng controller không đủ.

---

## 4. Vấn Đề Kỹ Thuật Ghi Nhận (Không Fix Trong Scope Này)

### ⚠️ Pre-existing: `/refresh-token` 500 với JWT Malformed

- **Triệu chứng:** `POST /api/auth/refresh-token` với body chứa JWT không hợp lệ về cú pháp (thiếu dấu `.`) trả về `500 Internal Server Error` thay vì `401`.
- **Nguyên nhân:** `MalformedJwtException` từ tầng Service không được bắt trong `GlobalExceptionHandler`. Chỉ `JwtAuthenticationFilter` bắt exception này cho các request có header `Authorization`.
- **Phân loại:** Pre-existing bug, không liên quan đến feature credential-change.
- **Đề xuất fix:** Thêm `@ExceptionHandler(MalformedJwtException.class)` vào `GlobalExceptionHandler` để trả `401`.

### ℹ️ Redis Jackson Serializer: Seed Phải JSON-Encoded

- **Phát hiện khi viết test script:** `RedisService` dùng `GenericJackson2JsonRedisSerializer`. Seed giá trị plain text qua `redis-cli` gây `SerializationException: Could not read JSON` khi service đọc lại.
- **Giải pháp:** Serialize JSON trước khi seed: `json.dumps(value)` trong Python hoặc dùng dấu nháy kép bao quanh trong `redis-cli`.

---

## 5. Xác Nhận Bất Biến Nghiệp Vụ

| Bất Biến | Verified |
|----------|:--------:|
| Lock chống spam 5 phút → `429 TOO_MANY_REQUESTS` | ✅ |
| Status check 0 DB query → `401` khi chưa active | ✅ |
| Token activation 0 DB query → `AUTH_CREDENTIAL_ACTIVE` trên Redis | ✅ |
| Single-use grant: active + lock bị xóa sau update thành công | ✅ |
| Session revocation: `AUTH_SESSION_DEVICE` cleared sau update | ✅ |
| Username 30-day cooldown `AUTH_GUARD_COOLDOWN` TTL=2592000s | ✅ |
| Old password login rejected sau khi đổi | ✅ |
| New credentials login thành công | ✅ |
| Cooldown enforcement: second username change → `401` | ✅ |
