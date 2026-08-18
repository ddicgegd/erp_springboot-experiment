# 02 - MODULE IAM API DOCUMENTATION

## 1. Overview & Authentication
Phân hệ Identity & Access Management (IAM) cung cấp toàn bộ các API liên quan đến xác thực người dùng, đăng ký, kích hoạt email, phân quyền, xoay vòng Refresh Token, quản lý phiên và cập nhật thông tin cá nhân.

- **Base Path:** `/api/auth`
- **Authentication Strategy:** Stateless Bearer JWT (`Authorization: Bearer <token>`).

---

## 2. API Endpoints Specification

### 2.1 User Login
Xác thực thông tin tài khoản và sinh cặp Access Token (ngắn hạn) + Refresh Token (dài hạn).

- **HTTP Method:** `POST`
- **Path:** `/api/auth/login`
- **Authentication:** None (Public)

**Request Body (`UserLoginRequest`):**
```json
{
  "username": "john_doe",     // Required: 3-50 chars
  "password": "Password123!", // Required: min 6 chars
  "deviceInfo": {             // Optional: Device tracking for session audit
    "deviceName": "Chrome on MacOS",
    "ipAddress": "192.168.1.10"
  }
}
```

**Success Response (`200 OK`):**
```json
{
  "code": 1000,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
    "refreshToken": "7f8b9a10-234e-4f6b...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": 1,
      "username": "john_doe",
      "email": "john@example.com",
      "fullName": "John Doe",
      "roles": ["ROLE_CUSTOMER"]
    }
  }
}
```

**Error Responses:**
- `401 Unauthorized`: Mã lỗi `1002` (Tài khoản hoặc mật khẩu không chính xác).
- `403 Forbidden`: Mã lỗi `1003` (Tài khoản bị khóa hoặc chưa kích hoạt).

**Code Examples:**
```bash
# cURL
curl -X POST https://api.erp.local/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john_doe","password":"Password123!"}'
```
```javascript
// JavaScript Fetch
const res = await fetch('https://api.erp.local/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'john_doe', password: 'Password123!' })
});
const data = await res.json();
```
```python
# Python Requests
import requests
res = requests.post('https://api.erp.local/api/auth/login', json={
    'username': 'john_doe',
    'password': 'Password123!'
})
```

---

### 2.2 User Registration
Tạo tài khoản mới và phát hành mã xác thực email.

- **HTTP Method:** `POST`
- **Path:** `/api/auth/register`
- **Request Body (`UserRegisterRequest`):**
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "Password123!",
  "fullName": "John Doe"
}
```
- **Success Response (`200 OK`):** `RegisterResponse` kèm thông báo kiểm tra email để kích hoạt.

---

### 2.3 Refresh Token Rotation
Lấy Access Token mới bằng Refresh Token hợp lệ.

- **HTTP Method:** `POST`
- **Path:** `/api/auth/refresh-token`
- **Request Body (`RefreshTokenRequest`):**
```json
{
  "refreshToken": "7f8b9a10-234e-4f6b-8712-89a7123bcdef"
}
```
- **Success Response (`200 OK`):** `AuthResponse` với cặp token mới. Token cũ bị thu hồi ngay lập tức trong Redis.

---

### 2.4 User Profile & Avatar Management
- `GET /api/auth/me`: Lấy thông tin cá nhân của người dùng hiện tại (Yêu cầu Bearer Token).
- `PUT /api/auth/me`: Cập nhật `fullName`, `phoneNumber`, `address`.
- `POST /api/auth/me/avatar`: Upload file avatar (Multipart form-data: `file`). Lưu trữ trên MinIO/S3.
- `PUT /api/auth/change-username`: Đổi tên đăng nhập (Áp dụng quy tắc Cooldown 30 ngày).
