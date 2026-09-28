# 📖 API Documentation: IAM User Profile, Birthday & Username Lifecycle Management
## *Hệ thống ERP Spring Boot 3.5.0 — Quản Lý Hồ Sơ Cá Nhân & Kiểm Soát Thời Hạn Đổi Định Danh*

Tài liệu đặc tả chi tiết toàn bộ các API thuộc phân hệ Quản lý Hồ sơ Người dùng (User Profile), cơ chế khóa thời hạn đổi Username trên Redis (30 ngày), và vòng đời quản lý Ngày sinh nhật (Flyway V19 / 365-Day Cooldown).

---

## 1. 🌐 Tổng quan & Xác thực (Overview & Authentication)

- **Base URL:** `http://localhost:8080/api/auth`
- **Content-Type:** `application/json`
- **Cơ chế xác thực:** Bắt buộc đính kèm JWT Bearer Token trong Request Header:
  ```http
  Authorization: Bearer <accessToken>
  ```
- **Cấu trúc Envelope chuẩn (`Response<T>`):**
  ```json
  {
    "status": {
      "code": 200,
      "message": "Mô tả kết quả thành công"
    },
    "data": { ... }
  }
  ```

---

## 2. 📋 Bảng Tổng Hợp Endpoints

| Method | Endpoint | Mô tả chức năng | Quyền truy cập | Cơ chế Cooldown / Khóa |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/auth/me` | Lấy toàn bộ hồ sơ cá nhân & hạn đổi username | `Authenticated` | Đọc TTL từ Redis |
| `PUT` | `/api/auth/me` | Cập nhật họ tên, SĐT, ngày sinh, ẩn năm sinh | `Authenticated` | Cooldown 365 ngày cho ngày sinh |
| `PUT` | `/api/auth/change-username` | Đổi tên đăng nhập | `Authenticated` | Khóa 30 ngày trên Redis + Revoke token |
| `POST` | `/api/auth/me/avatar` | Tải lên ảnh đại diện lên MinIO S3 | `Authenticated` | Max 5MB, Magic Bytes Check |
| `GET` | `/api/auth/validate-reset-token` | Kiểm tra token & lấy username tài khoản | `Public` | TTL Token 20 phút |

---

## 3. 🛠️ Đặc Tả Chi Tiết Từng Endpoint

### 3.1. `GET /api/auth/me` — Lấy Hồ Sơ Cá Nhân & Hạn Đổi Tên

Lấy thông tin tài khoản hiện tại. Tự động kiểm tra trên Redis xem tài khoản có đang trong thời hạn khóa đổi username (30 ngày) hay không để trả về trường `usernameCooldownUntil`.

- **Method:** `GET`
- **Path:** `/api/auth/me`
- **Headers:** `Authorization: Bearer <accessToken>`

#### Response Schema (`MyProfileResponse`):
| Thuộc tính | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| `id` | String | Mã ID người dùng |
| `username` | String | Tên đăng nhập hiện tại |
| `email` | String | Địa chỉ email tài khoản |
| `fullName` | String | Họ và tên đầy đủ |
| `phoneNumber` | String | Số điện thoại liên hệ |
| `usernameCooldownUntil` | String (`yyyy-MM-dd HH:mm:ss`) | Thời điểm hết hạn khóa đổi tên (trả về `null` nếu được đổi bình thường) |
| `dateOfBirth` | String (`yyyy-MM-dd`) | Ngày sinh nhật |
| `avatarUrl` | String | Đường dẫn ảnh đại diện trên MinIO |
| `gender` | String | Giới tính: `MALE`, `FEMALE`, `OTHER` |
| `rank` | String | Cấp bậc thành viên: `MEMBER`, `SILVER`, `GOLD`, `PLATINUM` |
| `status` | String | Trạng thái tài khoản: `ACTIVE`, `INACTIVE`, `LOCKED` |
| `roles` | Array\<String\> | Danh sách quyền hạn: `["ROLE_USER"]` |

#### Response Ví dụ 1: Đang trong hạn 30 ngày (Không được đổi username)
```json
{
  "status": {
    "code": 200,
    "message": "Success"
  },
  "data": {
    "id": "100",
    "username": "alex_developer",
    "email": "alex@example.com",
    "fullName": "Alex Developer",
    "phoneNumber": "0901234567",
    "usernameCooldownUntil": "2026-10-26 20:15:00",
    "dateOfBirth": "1998-09-26",
    "avatarUrl": "https://s3.ddicg.com/avatars/user-100.png",
    "gender": "MALE",
    "rank": "PLATINUM",
    "status": "ACTIVE",
    "roles": ["ROLE_USER"]
  }
}
```

#### Response Ví dụ 2: Hết hạn hoặc chưa đổi bao giờ (`usernameCooldownUntil = null`)
```json
{
  "status": {
    "code": 200,
    "message": "Success"
  },
  "data": {
    "id": "100",
    "username": "alex_developer",
    "email": "alex@example.com",
    "fullName": "Alex Developer",
    "phoneNumber": "0901234567",
    "usernameCooldownUntil": null,
    "dateOfBirth": "1998-09-26",
    "avatarUrl": "https://s3.ddicg.com/avatars/user-100.png",
    "gender": "MALE",
    "rank": "PLATINUM",
    "status": "ACTIVE",
    "roles": ["ROLE_USER"]
  }
}
```

---

### 3.2. `PUT /api/auth/change-username` — Đổi Tên Đăng Nhập (Cài Khóa Redis 1 Tháng)

Đổi tên đăng nhập tài khoản. Khi thành công, hệ thống tự động:
1. Lưu tên mới vào CSDL Oracle.
2. Cài đặt khóa Cooldown 1 tháng (30 ngày) trên Redis (`auth:guard:cooldown:{userId}`).
3. Thu hồi toàn bộ Access/Refresh Token cũ, ép buộc đăng nhập lại.

- **Method:** `PUT`
- **Path:** `/api/auth/change-username`
- **Headers:** `Authorization: Bearer <accessToken>`, `Content-Type: application/json`

#### Request Body:
```json
{
  "newUsername": "alex_pro_2026"
}
```

#### Ràng buộc nghiệp vụ:
- Độ dài: từ **3 đến 50 ký tự**.
- **Không chứa ký tự `@`** (chống xung đột phân giải Email/Username).
- Tên chưa tồn tại trên hệ thống.
- Không trong thời gian hạn 30 ngày.

#### Response Thành công (`200 OK`):
```json
{
  "status": {
    "code": 200,
    "message": "Đổi tên đăng nhập thành công. Vui lòng đăng nhập lại."
  },
  "data": null
}
```

#### Phản hồi Lỗi Thường Gặp:
- **`401 UNAUTHORIZED / INVALID_CREDENTIALS` (Đang trong hạn 30 ngày):**
  ```json
  {
    "status": {
      "code": 401,
      "message": "Bạn chỉ được đổi tên đăng nhập tối đa 1 lần mỗi tháng. Vui lòng quay lại sau."
    },
    "data": null
  }
  ```
- **`401 UNAUTHORIZED / INVALID_CREDENTIALS` (Trùng tên đã có):**
  ```json
  {
    "status": {
      "code": 401,
      "message": "Tên đăng nhập mới đã tồn tại trên hệ thống."
    },
    "data": null
  }
  ```

---

### 3.3. `PUT /api/auth/me` — Cập Nhật Hồ Sơ Cá Nhân & Ngày Sinh

Cập nhật các thông tin hồ sơ của người dùng.

- **Method:** `PUT`
- **Path:** `/api/auth/me`
- **Headers:** `Authorization: Bearer <accessToken>`, `Content-Type: application/json`

#### Request Body Schema (`UpdateProfileRequest`):
```json
{
  "fullName": "Alex Developer",
  "phoneNumber": "0901234567",
  "dateOfBirth": "1998-09-26",
  "gender": "MALE",
  "avatarUrl": "https://s3.ddicg.com/avatars/custom.png"
}
```

#### Ràng buộc nghiệp vụ:
- `dateOfBirth`: Định dạng chuẩn `yyyy-MM-dd`, bắt buộc là ngày quá khứ (`@Past`), độ tuổi từ **10 đến 120 tuổi**.
- **Cooldown Ngày sinh:** Không được phép sửa đổi lại ngày sinh nếu lần sửa trước đó chưa đủ **365 ngày** (1 năm).

#### Phản hồi Lỗi:
- **`400 BAD_REQUEST / VALIDATION_FAILED` (Sai độ tuổi / ngày tương lai):**
  ```json
  {
    "status": {
      "code": 400,
      "message": "Ngày sinh không hợp lệ hoặc độ tuổi phải từ 10 đến 120 tuổi."
    },
    "data": null
  }
  ```
- **`401 UNAUTHORIZED / INVALID_CREDENTIALS` (Vi phạm Cooldown 1 năm):**
  ```json
  {
    "status": {
      "code": 401,
      "message": "Bạn chỉ được phép cập nhật ngày sinh tối đa 1 lần mỗi năm. Vui lòng liên hệ CSKH nếu cần hỗ trợ."
    },
    "data": null
  }
  ```

---

### 3.4. `POST /api/auth/me/avatar` — Tải Lên Ảnh Đại Diện An Toàn

Upload ảnh đại diện trực tiếp lên MinIO Storage. Tự động xóa file ảnh cũ để tiết kiệm dung lượng.

- **Method:** `POST`
- **Path:** `/api/auth/me/avatar`
- **Headers:** `Authorization: Bearer <accessToken>`, `Content-Type: multipart/form-data`
- **Form Param:** `file` (MultipartFile)

#### Ràng buộc an toàn:
- Dung lượng tối đa: **5MB**.
- Định dạng nhị phân (Magic Bytes): `JPG`, `JPEG`, `PNG`, `GIF`.

---

## 4. 💻 Hướng Dẫn Tích Hợp Frontend (TypeScript Example)

```typescript
// 1. Interface DTO
export interface MyProfile {
  id: string;
  username: string;
  email: string;
  fullName: string;
  phoneNumber?: string;
  usernameCooldownUntil: string | null; // "2026-10-26 20:15:00" hoặc null
  dateOfBirth?: string; // "1998-09-26"
  avatarUrl?: string;
  gender?: 'MALE' | 'FEMALE' | 'OTHER';
  rank?: string;
  status: string;
}

// 2. Kiểm tra điều kiện đổi username trên UI
export function checkCanChangeUsername(profile: MyProfile): { canChange: boolean; message: string } {
  if (profile.usernameCooldownUntil) {
    return {
      canChange: false,
      message: `Bạn đã đổi tên gần đây. Có thể đổi lại sau: ${profile.usernameCooldownUntil}`
    };
  }
  return {
    canChange: true,
    message: "Bạn có thể đổi tên đăng nhập."
  };
}
```

---

## 5. 📋 Bảng Mã Lỗi (Error Matrix)

| HTTP Status | ErrorCode | Thông Báo Backend | Hành Vi Frontend |
| :--- | :--- | :--- | :--- |
| `400` | `VALIDATION_FAILED` | *"Tên đăng nhập mới phải từ 3 đến 50 ký tự."* | Validate Client trước khi submit form. |
| `400` | `VALIDATION_FAILED` | *"Ngày sinh không hợp lệ hoặc độ tuổi phải từ 10 đến 120 tuổi."* | Báo lỗi ô chọn ngày sinh. |
| `401` | `INVALID_CREDENTIALS` | *"Bạn chỉ được đổi tên đăng nhập tối đa 1 lần mỗi tháng..."* | Disable nút đổi tên, hiển thị `usernameCooldownUntil`. |
| `401` | `INVALID_CREDENTIALS` | *"Tên đăng nhập mới đã tồn tại trên hệ thống."* | Hiển thị cảnh báo tên trùng lặp. |
| `401` | `INVALID_CREDENTIALS` | *"Bạn chỉ được phép cập nhật ngày sinh tối đa 1 lần mỗi năm..."* | Hiển thị thông báo hướng dẫn liên hệ CSKH. |
| `401` | `UNAUTHORIZED` | *"Full authentication is required..."* | Xóa session và chuyển hướng về `/login`. |
