# 📖 API Documentation: Token-Based Credential Change (5-Minute Redis Grant)
## *Hệ thống ERP Spring Boot 3.5.0 — Phân hệ IAM & Bảo Mật Xác Thực*

Tài liệu đặc tả toàn diện luồng xác thực 2 bước đổi Tên đăng nhập (`username`) và Mật khẩu (`password`) thông qua mã kích hoạt gửi về Email, cửa sổ cấp quyền **5 phút** trên Redis, cơ chế Fast Polling (100% In-Memory) và endpoint cập nhật hợp nhất.

---

## 1. 🌐 Tổng quan Luồng Nghiệp Vụ (Architecture & Workflow)

```
[BƯỚC 1: GỬI YÊU CẦU ĐỔI THÔNG TIN]
POST /api/auth/credential-change/request (Bearer JWT)
  │
  ├─ 1. Khóa chống spam 5 phút trên Redis: "auth:action:credential:lock:{userId}"
  │     (Nếu bấm gửi lại trong 5 phút -> Chặn 429 TOO_MANY_REQUESTS)
  ├─ 2. Sinh UUID rawToken, băm SHA-256 lưu Redis: "auth:action:credential:token:{hash}" (TTL: 5m)
  └─ 3. Gửi email chứa liên kết kích hoạt: /security/credentials?token={rawToken}

─────────────────────────────────────────────────────────────────────────────────────────

[BƯỚC 2: KÍCH HOẠT TOKEN TỪ EMAIL (CROSS-DEVICE ACTIVATION)]
GET /api/auth/credential-change/activate?token={rawToken} (Public)
  │
  ├─ 1. Băm SHA-256 token và kiểm tra trên Redis (0 TRUY VẤN DATABASE):
  │     • Nếu không tồn tại / hết hạn -> Ném 401 INVALID_CREDENTIALS
  ├─ 2. Kích hoạt quyền ACTIVE cho userId trên Redis:
  │     Key: "auth:action:credential:active:{userId}"  |  TTL: Thời gian còn lại của 5 phút
  └─ 3. Xóa token pending cũ trên Redis

─────────────────────────────────────────────────────────────────────────────────────────

[BƯỚC 3: FAST POLLING KIỂM TRA TRẠNG THÁI (100% IN-MEMORY REDIS)]
GET /api/auth/credential-change/status (Bearer JWT)
  │
  ├─ 1. Đọc trực tiếp từ Redis: "auth:action:credential:active:{userId}" (0 TRUY VẤN DATABASE)
  │     • Đang ACTIVE  -> HTTP 200 OK: { "status": "ACTIVE", "remainingSeconds": 285, "expiresAt": "..." }
  │     • Chưa active / Hết hạn -> HTTP 401 UNAUTHORIZED: "Phiên xác thực chưa được kích hoạt hoặc đã hết hạn."
  └─ 2. Frontend dùng polling để tự động mở khóa form khi người dùng click link email từ thiết bị khác

─────────────────────────────────────────────────────────────────────────────────────────

[BƯỚC 4: CẬP NHẬT THÔNG TIN DUY NHẤT (SINGLE UNIFIED UPDATE)]
PUT /api/auth/update-credentials (Bearer JWT)
  │
  ├─ 1. Fast Guard: Kiểm tra "auth:action:credential:active:{userId}" -> Nếu false ném 401 (0 DB Query)
  ├─ 2. Nếu có newUsername:
  │     • Kiểm tra Cooldown 30 ngày (AUTH_GUARD_COOLDOWN). Nếu vi phạm -> Ném 401.
  │     • Kiểm tra định dạng (3-50 ký tự, không chứa '@') & kiểm tra trùng lặp trên DB.
  │     • Cập nhật name và thiết lập Cooldown 30 ngày trên Redis.
  ├─ 3. Nếu có newPassword:
  │     • Kiểm tra khớp confirmPassword -> Mã hóa BCrypt & cập nhật password.
  ├─ 4. Single-action: Thu hồi ngay quyền ACTIVE và LOCK trên Redis (chống Replay Attack).
  └─ 5. Thu hồi mọi Access/Refresh Token cũ trên toàn bộ thiết bị (revokeAllUserTokens).
```

---

## 2. 📋 Bảng Danh Mục Endpoints

| Method | Endpoint | Quyền | Mục đích | Cơ chế Bảo mật |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/credential-change/request` | `Bearer JWT` | Yêu cầu gửi email xác thực | Khóa chống spam đồng bộ 5 phút |
| `GET` | `/api/auth/credential-change/activate` | `Public` | Kích hoạt quyền từ link email | 100% Redis In-Memory |
| `GET` | `/api/auth/credential-change/status` | `Bearer JWT` | Polling kiểm tra trạng thái | 100% Redis, trả 401 khi chưa active |
| `PUT` | `/api/auth/update-credentials` | `Bearer JWT` | Cập nhật Username / Password | Single-use Token + Revoke all sessions |

---

## 3. 🛠️ Chi Tiết API Contracts

### 3.1. `POST /api/auth/credential-change/request` — Yêu Cầu Gửi Mail Xác Thực
- **Headers:** `Authorization: Bearer <accessToken>`
- **Response Thành Công (`200 OK`):**
  ```json
  {
    "status": {
      "code": 200,
      "message": "Liên kết xác thực thay đổi thông tin đăng nhập đã được gửi đến a***@example.com. Vui lòng kiểm tra hộp thư."
    },
    "data": null
  }
  ```
- **Response Khi Bị Khóa Gửi Lại (`429 TOO_MANY_REQUESTS`):**
  ```json
  {
    "status": {
      "code": 429,
      "message": "Yêu cầu xác thực trước đó của bạn vẫn đang có hiệu lực. Vui lòng kiểm tra hộp thư hoặc thử lại sau."
    },
    "data": null
  }
  ```

---

### 3.2. `GET /api/auth/credential-change/activate` — Kích Hoạt Token từ Link Email
- **Method:** `GET`
- **Path:** `/api/auth/credential-change/activate?token={token}`
- **Quyền:** `Public` (kích hoạt từ bất kỳ thiết bị nào).
- **Response Thành Công (`200 OK`):**
  ```json
  {
    "status": {
      "code": 200,
      "message": "Kích hoạt quyền đổi thông tin thành công. Bạn có 5 phút để cập nhật."
    },
    "data": null
  }
  ```

---

### 3.3. `GET /api/auth/credential-change/status` — Fast Polling Kiểm Tra Trạng Thái
- **Headers:** `Authorization: Bearer <accessToken>`
- **Response Khi Đang ACTIVE (`200 OK`):**
  ```json
  {
    "status": {
      "code": 200,
      "message": "Phiên đổi thông tin đang hoạt động."
    },
    "data": {
      "status": "ACTIVE",
      "remainingSeconds": 285,
      "expiresAt": "2026-09-27 10:35:00"
    }
  }
  ```
- **Response Khi Chưa Active / Hết Hạn (`401 UNAUTHORIZED / INVALID_CREDENTIALS`):**
  ```json
  {
    "status": {
      "code": 401,
      "message": "Phiên xác thực chưa được kích hoạt hoặc đã hết hạn."
    },
    "data": null
  }
  ```

---

### 3.4. `PUT /api/auth/update-credentials` — Cập Nhật Thông Tin Hợp Nhất

Cập nhật Tên đăng nhập mới, Mật khẩu mới hoặc **cả hai cùng lúc** mà **không cần truyền token trong Request Body**.

- **Headers:** `Authorization: Bearer <accessToken>`, `Content-Type: application/json`

#### Request Body Schema (`UpdateCredentialsRequest`):
```json
{
  "newUsername": "alex_pro_2026",
  "newPassword": "NewSecurePassword123@",
  "confirmPassword": "NewSecurePassword123@"
}
```
*(Nếu chỉ đổi username: truyền chỉ `newUsername`. Nếu chỉ đổi password: truyền `newPassword` và `confirmPassword`).*

#### Response Thành Công (`200 OK`):
```json
{
  "status": {
    "code": 200,
    "message": "Cập nhật thông tin đăng nhập thành công. Vui lòng đăng nhập lại."
  },
  "data": null
}
```

---

## 4. 📋 Bảng Mã Lỗi (Error Matrix)

| HTTP Code | ErrorCode | Thông Báo Backend | Hướng Dẫn Xử Lý UI |
| :--- | :--- | :--- | :--- |
| `400` | `VALIDATION_FAILED` | *"Vui lòng cung cấp ít nhất Tên đăng nhập mới hoặc Mật khẩu mới..."* | Báo người dùng nhập ít nhất 1 trường. |
| `400` | `VALIDATION_FAILED` | *"Mật khẩu xác nhận không trùng khớp."* | Báo đỏ ô xác nhận mật khẩu. |
| `400` | `INVALID_FORMAT` | *"Tên đăng nhập không được chứa ký tự '@'."* | Validate định dạng tên đăng nhập. |
| `401` | `INVALID_CREDENTIALS` | *"Bạn chưa xác thực qua email hoặc phiên đổi thông tin đã hết hạn."* | Điều hướng về trang gửi yêu cầu xác thực. |
| `401` | `INVALID_CREDENTIALS` | *"Bạn chỉ được đổi tên đăng nhập tối đa 1 lần mỗi tháng..."* | Hiển thị Toast thông báo hạn 30 ngày cho Username. |
| `401` | `INVALID_CREDENTIALS` | *"Tên đăng nhập mới đã tồn tại trên hệ thống."* | Báo tên bị trùng lặp. |
| `429` | `TOO_MANY_REQUESTS` | *"Yêu cầu xác thực trước đó của bạn vẫn đang có hiệu lực..."* | Khóa nút gửi lại email trong 5 phút. |

---

## 5. ⚠️ Gotchas & Known Issues

### 5.1. Security Config: Public Endpoint Phải Khai Báo Tường Minh

> **Phát hiện qua Backtest (2026-09-27)**

Endpoint `GET /api/auth/credential-change/activate` **phải được khai báo trong `PUBLIC_AUTH_ENDPOINTS`** của `SecurityConfiguration.java`. Spring Security sử dụng chiến lược **deny-by-default** — bất kỳ endpoint nào không được liệt kê tường minh trong whitelist đều bị chặn 403 trước khi request đến controller.

**Lý do dễ bỏ sót:** Endpoint này annotated `@PermitAll` / không yêu cầu `@PreAuthorize` ở tầng controller, nhưng Security Filter Chain xử lý trước — annotation controller không có tác dụng nếu filter đã chặn.

**File cần cập nhật khi thêm endpoint Public mới:**
```java
// src/main/java/com/ddicg/erp/core/config/SecurityConfiguration.java
private static final String[] PUBLIC_AUTH_ENDPOINTS = {
    // ... existing entries ...
    "/api/auth/credential-change/activate"  // ← PHẢI có dòng này
};
```

### 5.2. Redis Value Phải JSON-Encoded (Jackson Serializer)

`RedisService` sử dụng `GenericJackson2JsonRedisSerializer`. Khi đọc một giá trị, nó deserialize JSON — giá trị plain text (không có dấu nháy kép bao quanh) gây `SerializationException`.

**Ảnh hưởng đến Integration Test / Script Seed:**
- ❌ **Sai:** `redis-cli setex key 300 ACTIVE` → lưu `ACTIVE` (plain text) → khi đọc: `JsonParseException`
- ✅ **Đúng:** `redis-cli setex key 300 '"ACTIVE"'` → lưu `"ACTIVE"` (JSON string) → deserialize thành công

Hoặc trong Python test script:
```python
import json
json_val = json.dumps(value)  # "ACTIVE" → '"ACTIVE"'
redis_cli("setex", key, "300", json_val)
```

### 5.3. Pre-existing Bug: `/refresh-token` 500 với JWT Malformed

`POST /api/auth/refresh-token` trả về `500 Internal Server Error` (thay vì `401`) khi body chứa JWT có cú pháp sai (không đủ 2 dấu `.`). `MalformedJwtException` ném từ tầng Service không được catch trong `GlobalExceptionHandler`. Không liên quan đến luồng credential-change; ghi nhận để fix độc lập.
