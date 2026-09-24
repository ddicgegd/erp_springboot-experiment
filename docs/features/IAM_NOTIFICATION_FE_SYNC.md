# TÀI LIỆU ĐỒNG BỘ FRONTEND: LUỒNG XÁC THỰC EMAIL & KHÔI PHỤC TÀI KHOẢN (IAM & NOTIFICATION)

> **Mục đích**: Đồng bộ toàn diện giữa Backend (BE) và Frontend (FE) về các thay đổi trong API Contract, cơ chế phân tách Token, luồng điều hướng màn hình (Routing), và các mẫu thông báo Email mới.

---

## 1. TỔNG QUAN THAY ĐỔI TRỌNG TÂM (EXECUTIVE SUMMARY)

| Nghiệp vụ | Trạng thái cũ (Cần loại bỏ ở FE) | Trạng thái mới (FE cần đồng bộ) |
| :--- | :--- | :--- |
| **Kích hoạt tài khoản** | Nhận cả `token` lẫn `code` trên URL; nhầm lẫn giữa mã đổi MK và mã kích hoạt. | Chỉ dùng **1 tham số duy nhất `token`**: `GET /api/auth/verify-email?token={token}`. |
| **Gửi lại mã xác thực** | Không có API riêng (phải gọi lại register/login để trigger). | Có API riêng: `POST /api/auth/resend-verification` với body `{"email": "..."}`. |
| **Đặt lại mật khẩu** | Truyền `code`/`token` qua query param kết hợp body. | Chuẩn hóa **100% qua Request Body**: `POST /api/auth/reset-password` (không truyền query param). |
| **Đổi tên đăng nhập** | Có trường `token` trong request body. | **Bắt buộc có Bearer Token** (Đã đăng nhập). Xóa trường `token` trong body `ChangeUsernameRequest`. |
| **Thời hạn Token** | Không nhất quán (5 phút - 24 giờ). | Đồng bộ chuẩn: **15 phút** cho Xác thực email; **20 phút** cho Khôi phục mật khẩu. |
| **Rate Limit / Anti-spam** | Không giới hạn tần suất. | Giới hạn **Cooldown 60s** và **Quota 5 lần/giờ**. Trả về HTTP `429 TOO_MANY_REQUESTS`. |

---

## 2. PHÂN TÁCH 2 MIỀN NGHIỆP VỤ ĐỘC LẬP (TOKEN DOMAIN SEPARATION)

> ⚠️ **LƯU Ý QUAN TRỌNG CHO FE**: Hệ thống đã tách riêng 2 loại Token độc lập hoàn toàn trong Redis. **Không thể dùng chéo Token của luồng này cho luồng kia.**

```
                                  ┌──────────────────────────────────────────────────────────┐
                                  │                NGƯỜI DÙNG / EMAIL CLIENT                │
                                  └────────────┬─────────────────────────────┬───────────────┘
                                               │                             │
                        (1) Click link / Copy token                   (2) Click link / Copy token
                             từ Email Xác Thực                            từ Email Khôi Phục
                                               │                             │
                                               ▼                             ▼
                    ┌──────────────────────────────────────┐     ┌──────────────────────────────────────┐
                    │       FE Route: /verify-email        │     │       FE Route: /reset-password      │
                    └──────────────────┬───────────────────┘     └──────────────────┬───────────────────┘
                                       │                                            │
                                       ▼                                            ▼
┌──────────────────────────────────────────────────────────────┐ ┌──────────────────────────────────────────────────────────────┐
│                  LUỒNG 1: XÁC THỰC EMAIL                     │ │                  LUỒNG 2: ĐẶT LẠI MẬT KHẨU                   │
│                                                              │ │                                                              │
│ • Token Store: AUTH_VERIFICATION_TOKEN                       │ │ • Token Store: AUTH_RECOVERY_TOKEN                           │
│ • TTL: 15 phút                                               │ │ • TTL: 20 phút                                               │
│ • Mục đích: Chuyển User từ INACTIVE -> ACTIVE                │ │ • Mục đích: Cấp quyền đổi mật khẩu cho User ACTIVE           │
│                                                              │ │                                                              │
│ API Tiêu thụ:                                                │ │ API Tiêu thụ:                                                │
│ 1. GET /api/auth/verify-email?token={token}                  │ │ 1. GET  /api/auth/validate-reset-token?token={token}        │
│ 2. POST /api/auth/resend-verification                        │ │ 2. POST /api/auth/reset-password                           │
└──────────────────────────────────────────────────────────────┘ └──────────────────────────────────────────────────────────────┘
```

---

## 3. CHI TIẾT API CONTRACT CHO FRONTEND

### 3.1. Xác thực Email kích hoạt tài khoản

- **Endpoint**: `GET /api/auth/verify-email`
- **Quyền truy cập**: Public (Không cần đăng nhập)
- **Query Parameters**:
  - `token` *(string, required)*: Mã token UUID nhận được từ email kích hoạt.
- **Request mẫu**:
  ```http
  GET /api/auth/verify-email?token=8f0b154a-7140-4209-8438-fb14c33d0a21 HTTP/1.1
  ```
- **Response thành công (`200 OK`)**:
  ```json
  {
    "status": {
      "code": 200,
      "message": "Xác thực email thành công. Tài khoản của bạn đã được kích hoạt."
    },
    "data": null
  }
  ```
- **Xử lý FE**: Sau khi nhận `200 OK`, hiển thị thông báo thành công và chuyển hướng (Redirect) về trang Đăng nhập (`/login`).

---

### 3.2. Gửi lại email xác thực (Resend Verification)

- **Endpoint**: `POST /api/auth/resend-verification`
- **Quyền truy cập**: Public (Không cần đăng nhập)
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "email": "user@example.com"
  }
  ```
- **Response thành công (`200 OK`)**:
  ```json
  {
    "status": {
      "code": 200,
      "message": "Nếu email tồn tại trên hệ thống và chưa được kích hoạt, liên kết xác thực mới đã được gửi đến u***@example.com. Vui lòng kiểm tra."
    },
    "data": null
  }
  ```
- **Lưu ý Anti-Enumeration**: Backend luôn trả về `200 OK` với thông điệp chung dù email có tồn tại hay không. FE cần hiển thị thông báo thân thiện và kích hoạt đếm ngược Cooldown **60 giây** trên nút bấm.

---

### 3.3. Yêu cầu khôi phục mật khẩu (Forgot Password)

- **Endpoint**: `GET /api/auth/recover-account/{email}`
- **Quyền truy cập**: Public
- **Path Variable**: `email` *(string, required)*: Email cần khôi phục.
- **Response thành công (`200 OK`)**:
  ```json
  {
    "status": {
      "code": 200,
      "message": "Nếu email tồn tại trên hệ thống, liên kết khôi phục tài khoản đã được gửi đến u***@example.com. Vui lòng kiểm tra."
    },
    "data": null
  }
  ```

---

### 3.4. Kiểm tra tính hợp lệ của Token Đặt lại mật khẩu

- **Endpoint**: `GET /api/auth/validate-reset-token`
- **Quyền truy cập**: Public
- **Query Parameters**:
  - `token` *(string, required)*: Mã token UUID từ email khôi phục mật khẩu.
- **Request mẫu**:
  ```http
  GET /api/auth/validate-reset-token?token=a18cc5f6-8088-4b2a-a4a0-37bbc382b264 HTTP/1.1
  ```
- **Response thành công (`200 OK`)**:
  ```json
  {
    "status": {
      "code": 200,
      "message": "Mã token hợp lệ. Vui lòng thiết lập mật khẩu mới."
    },
    "data": "username_nguoi_dung"
  }
  ```
- **Xử lý FE**: Khi người dùng vào trang `/reset-password?token=...`, FE gọi API này để validate trước:
  - Nếu `200 OK`: Hiển thị form nhập mật khẩu mới.
  - Nếu lỗi `401`: Báo token hết hạn/không hợp lệ, hiển thị nút quay về trang Quên mật khẩu.

---

### 3.5. Thực hiện Đặt lại mật khẩu mới (Submit Reset Password)

- **Endpoint**: `POST /api/auth/reset-password`
- **Quyền truy cập**: Public
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "token": "a18cc5f6-8088-4b2a-a4a0-37bbc382b264",
    "newPassword": "SecurePassword123@",
    "confirmPassword": "SecurePassword123@"
  }
  ```
- **Response thành công (`200 OK`)**:
  ```json
  {
    "status": {
      "code": 200,
      "message": "Mật khẩu đã được thay đổi thành công. Vui lòng đăng nhập lại."
    },
    "data": null
  }
  ```
- **Xử lý FE**: Chuyển hướng người dùng về trang Đăng nhập (`/login`).

---

### 3.6. Đổi tên đăng nhập (Change Username)

- **Endpoint**: `PUT /api/auth/change-username`
- **Quyền truy cập**: **Authenticated** (Bắt buộc đính kèm `Authorization: Bearer <accessToken>`)
- **Request Body**:
  ```json
  {
    "newUsername": "new_awesome_username"
  }
  ```
- **Response thành công (`200 OK`)**:
  ```json
  {
    "status": {
      "code": 200,
      "message": "Đổi tên đăng nhập thành công. Vui lòng đăng nhập lại."
    },
    "data": null
  }
  ```
- **Lưu ý FE**: Sau khi đổi username thành công, toàn bộ session/refresh token cũ bị hủy. FE cần xóa token lưu ở client và đưa user về trang Login.

---

## 4. MA TRẬN ĐIỀU HƯỚNG & ROUTING PHÍA FRONTEND (FE ROUTING MATRIX)

| Route FE | Hành vi & Tương tác API |
| :--- | :--- |
| `/verify-email?token=...` | 1. Đọc `token` từ URL query.<br>2. Gọi `GET /api/auth/verify-email?token={token}`.<br>3. Hiển thị thông báo thành công $\rightarrow$ Nút/Tự động redirect về `/login`. |
| `/resend-verification` | 1. Form nhập email.<br>2. Submit `POST /api/auth/resend-verification`.<br>3. Hiển thị thông báo thành công + Bắt đầu đếm ngược 60 giây (Disable nút submit). |
| `/forgot-password` | 1. Form nhập email.<br>2. Submit `GET /api/auth/recover-account/{email}`.<br>3. Thông báo kiểm tra email khôi phục. |
| `/reset-password?token=...` | 1. Đọc `token` từ URL query.<br>2. Gọi `GET /api/auth/validate-reset-token?token={token}`.<br>3. Nếu hợp lệ: Hiển thị form nhập `newPassword` & `confirmPassword`.<br>4. Submit `POST /api/auth/reset-password` (gửi token trong Body).<br>5. Thành công $\rightarrow$ Redirect `/login`. |

---

## 5. ĐỒNG BỘ EMAIL TEMPLATE & DEEP LINKS

Cả 2 template email HTML gửi đi từ Backend đều đã được nâng cấp giao diện chuẩn Shadcn Bevel và tích hợp Deep Links:

1. **Email Xác thực tài khoản**:
   - **Nút bấm chính**: `KÍCH HOẠT TÀI KHOẢN` $\rightarrow$ Trỏ về: `${frontendUrl}/verify-email?token={token}`
   - **Hộp sao chép Token**: Cho phép user sao chép mã token dạng UUID.
   - **Ghi chú**: *"Mã token này chỉ sử dụng để Xác thực & Kích hoạt tài khoản."*
   - **Thời hạn**: **15 phút**.

2. **Email Khôi phục mật khẩu**:
   - **Nút bấm chính**: `ĐẶT LẠI MẬT KHẨU` $\rightarrow$ Trỏ về: `${frontendUrl}/reset-password?token={token}`
   - **Hộp sao chép Token**: Cho phép user sao chép mã token dạng UUID.
   - **Ghi chú**: *"Mã token này chỉ sử dụng để Đặt lại mật khẩu, không dùng cho xác thực kích hoạt tài khoản."*
   - **Thời hạn**: **20 phút**.

---

## 6. BẢNG MÃ LỖI & HƯỚNG DẪN XỬ LÝ GIAO DIỆN (ERROR HANDLING)

| HTTP Status | Error Code | Thông báo chi tiết từ BE | Hướng dẫn xử lý trên FE |
| :--- | :--- | :--- | :--- |
| **`401`** | `INVALID_CREDENTIALS` | *"Mã xác thực email không hợp lệ hoặc đã hết hạn."* | Token không tồn tại, đã hết hạn, hoặc bị dùng sai luồng (dùng token đổi pass cho verify email). Hiển thị nút bấm *"Gửi lại mã mới"*. |
| **`401`** | `INVALID_CREDENTIALS` | *"Tài khoản đang bị khóa."* | Hiển thị thông báo tài khoản bị khóa và liên hệ Quản trị viên. |
| **`429`** | `TOO_MANY_REQUESTS` | *"Bạn thao tác quá nhanh. Vui lòng thử lại sau."* | Đang trong thời gian Cooldown (60s). FE hiển thị Toast cảnh báo và đếm ngược. |
| **`429`** | `TOO_MANY_REQUESTS` | *"Bạn đã vượt quá số lần yêu cầu... trong 1 giờ."* | Đã vượt Quota (5 lần/h). FE yêu cầu người dùng thử lại sau 1 tiếng. |
| **`403`** | `ACCESS_DENIED` | *"Tài khoản chưa được kích hoạt..."* | Xảy ra khi cố đổi mật khẩu cho user chưa kích hoạt. Hướng dẫn user kích hoạt trước. |
| **`400`** | `INVALID_FORMAT` | *"Email không đúng định dạng."* | Validate định dạng email phía Client trước khi gửi. |
