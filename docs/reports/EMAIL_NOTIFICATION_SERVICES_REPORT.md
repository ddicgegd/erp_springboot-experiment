# BÁO CÁO TOÀN DIỆN: CÁC DỊCH VỤ EMAIL & ĐẶC TẢ CHI TIẾT ENDPOINT (REQUEST / RESPONSE)
## *Hệ thống ERP Spring Boot — Phân hệ IAM & Notification*

**Ngày lập báo cáo:** 2026-09-28  
**Trạng thái hệ thống:** Đã đồng bộ 100% URL về Frontend Port 3000 (`http://localhost:3000`)  
**Tài liệu tham chiếu:** `docs/decisions/ADR-002`, `CREDENTIAL_CHANGE_TOKEN_FLOW_DOCS.md`, RFC 7807 (Problem Details)

---

## 1. Tổng Quan Kiến Trúc & Danh Mục 4 Loại Email

Hệ thống email trong ERP Spring Boot hoạt động theo mô hình **Event-driven bất đồng bộ** qua Apache Kafka (`notification-email-topic`), bảo vệ bằng Redis (Rate Limiting 5 mail/phút + Deduplication Lock), và biên dịch nội dung qua template Thymeleaf chuẩn thiết kế shadcn/ui bevel.

```
[Phân Hệ Nghiệp Vụ (IAM / Scheduler)]
               │
               ▼
[Kafka Producer (NotificationEventProducer)]
               │
               ▼ (Topic: notification-email-topic)
[Kafka Consumer (NotificationEventConsumer)] ──► Kiểm tra Rate Limit & Dedup Lock (Redis)
               │
               ▼
[Thymeleaf Engine] ──► [JavaMailSender (Gmail SMTP 587)] ──► Người Dùng
```

### Bảng Ma Trận Phân Loại Email & Chức Năng Nghiệp Vụ

| # | Mã Template (`TemplateCode`) | Phân Hệ / Chức Năng Nghiệp Vụ | TTL Token | URL Gửi Trong Email (Port 3000) |
|---|---|---|:---:|---|
| **1** | `VERIFICATION_EMAIL` | **IAM - Quản lý Định danh**: Đăng ký mới, kích hoạt tài khoản, gửi lại mail xác thực | 15 phút | `http://localhost:3000/verify-email?token={token}` |
| **2** | `ACCOUNT_RECOVERY` | **IAM - Bảo mật & Khôi phục**: Quên mật khẩu, lấy lại quyền truy cập tài khoản | 20 phút | `http://localhost:3000/reset-password?token={token}` |
| **3** | `CREDENTIAL_CHANGE` | **IAM - Bảo mật Nâng cao**: Xác thực cấp quyền đổi Username / Password (Cross-device) | 5 phút | `http://localhost:3000/credential-change/activate?token={token}` |
| **4** | `BIRTHDAY_GREETING` | **Customer Care & Marketing**: Tự động chúc mừng sinh nhật và tặng voucher ưu đãi | 365 ngày | `http://localhost:3000/profile?voucher={code}` |

---

## 2. Đặc Tả Chi Tiết Từng Loại Email & Endpoint (Req / Res)

---

### LOẠI 1: `VERIFICATION_EMAIL` — Xác Thực Tài Khoản Người Dùng

#### 1.1. Chức năng nghiệp vụ
- Kích hoạt tài khoản người dùng sau khi đăng ký mới.
- Tự động phát lại email kích hoạt khi tài khoản trạng thái `INACTIVE` cố gắng đăng nhập.
- Cho phép người dùng chủ động yêu cầu gửi lại email xác thực nếu link cũ hết hạn (15 phút).
- Xác thực và mở khóa tài khoản thành `ACTIVE` khi click vào link gửi về hộp thư.

#### 1.2. Các Endpoints liên quan

---

#### Endpoint 1.1.1: Đăng ký tài khoản mới (Kích hoạt gửi email)
- **Method:** `POST`
- **Path:** `/api/auth/register`
- **Quyền:** `Public`
- **Headers:** `Content-Type: application/json`

**Request Body (`UserRegisterRequest`):**
```json
{
  "fullName": "Nguyễn Văn A",
  "name": "nguyenvana",
  "email": "nguyenvana@gmail.com",
  "password": "Password@123",
  "confirmPassword": "Password@123"
}
```

**Response Thành công (`200 OK`):**
```json
{
  "status": {
    "code": 200,
    "message": "OK"
  },
  "data": {
    "message": "Đăng ký tài khoản thành công. Vui lòng kiểm tra email để xác thực tài khoản."
  }
}
```

**Response Thất bại / Lỗi:**
- **400 Bad Request — Validation dữ liệu đầu vào thất bại:**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Dữ liệu gửi lên không hợp lệ.",
  "errors": {
    "email": "Email không đúng định dạng",
    "password": "Mật khẩu phải chứa ít nhất 6 ký tự"
  }
}
```
- **400 Bad Request — Mật khẩu xác nhận không khớp:**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Mật khẩu và xác nhận mật khẩu không khớp.",
  "errorCode": "INVALID_CREDENTIALS"
}
```
- **400 Bad Request — Tên đăng nhập hoặc Email đã tồn tại:**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Tên đăng nhập hoặc email đã được sử dụng.",
  "errorCode": "EXISTING_USER"
}
```

---

#### Endpoint 1.1.2: Đăng nhập tài khoản chưa kích hoạt (Tự động gửi lại email)
- **Method:** `POST`
- **Path:** `/api/auth/login`
- **Quyền:** `Public`
- **Headers:** `Content-Type: application/json`

**Request Body (`UserLoginRequest`):**
```json
{
  "usernameOrEmail": "nguyenvana",
  "password": "Password@123",
  "deviceInfo": {
    "deviceId": "web-chrome-uuid",
    "deviceType": "Desktop",
    "browserName": "Chrome",
    "ipAddress": "127.0.0.1"
  }
}
```

**Response Khi Tài Khoản `INACTIVE` (`403 Forbidden` / `400 Bad Request`):**
*(Hệ thống tự động sinh token mới TTL 15 phút, phát sự kiện Kafka gửi email xác thực kèm cơ chế cooldown 60s)*
```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "Tên đăng nhập đã tồn tại với email khác hoặc tài khoản chưa kích hoạt. Vui lòng kích hoạt qua email đã gửi hoặc sử dụng tên đăng nhập khác.",
  "errorCode": "INVALID_CREDENTIALS"
}
```

---

#### Endpoint 1.1.3: Yêu cầu gửi lại email xác thực
- **Method:** `POST`
- **Path:** `/api/auth/resend-verification`
- **Quyền:** `Public`
- **Headers:** `Content-Type: application/json`

**Request Body (`ResendVerificationRequest`):**
```json
{
  "email": "nguyenvana@gmail.com"
}
```

**Response Thành công (`200 OK`):**
```json
{
  "status": {
    "code": 200,
    "message": "OK"
  },
  "data": "Một liên kết xác thực mới đã được gửi đến email của bạn. Vui lòng kiểm tra hộp thư."
}
```

**Response Thất bại / Lỗi:**
- **404 Not Found — Email không tồn tại:**
```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Không tìm thấy người dùng với email: nguyenvana@gmail.com",
  "errorCode": "USER_NOT_FOUND"
}
```
- **400 Bad Request — Tài khoản đã được kích hoạt trước đó:**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Tài khoản của bạn đã được kích hoạt. Vui lòng đăng nhập.",
  "errorCode": "INVALID_CREDENTIALS"
}
```
- **429 Too Many Requests — Gửi yêu cầu quá dày (Spam Cooldown 60s):**
```json
{
  "type": "about:blank",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Bạn gửi yêu cầu quá thường xuyên. Vui lòng đợi 60 giây trước khi thử lại.",
  "errorCode": "TOO_MANY_REQUESTS"
}
```

---

#### Endpoint 1.1.4: Xác thực và kích hoạt tài khoản
- **Method:** `GET`
- **Path:** `/api/auth/verify-email?token={rawToken}`
- **Quyền:** `Public`
- **Query Parameters:** `token` (String, Required) — Mã token gửi kèm link email

**Response Thành công (`200 OK`):**
```json
{
  "status": {
    "code": 200,
    "message": "OK"
  },
  "data": "Tài khoản của bạn đã được xác thực thành công. Bạn có thể đăng nhập ngay bây giờ."
}
```

**Response Thất bại / Lỗi:**
- **401 Unauthorized — Token không tồn tại hoặc đã hết hạn (sau 15 phút):**
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Liên kết xác thực đã hết hạn hoặc không hợp lệ. Vui lòng yêu cầu liên kết mới.",
  "errorCode": "INVALID_CREDENTIALS"
}
```

---

### LOẠI 2: `ACCOUNT_RECOVERY` — Khôi Phục Thông Tin Tài Khoản (Quên Mật Khẩu)

#### 2.1. Chức năng nghiệp vụ
- Khôi phục quyền truy cập khi người dùng quên mật khẩu.
- Bảo vệ quyền riêng tư qua cơ chế Anti-Enumeration (không để lộ email có tồn tại hay không).
- Token khôi phục lưu trữ trên Redis bằng hàm băm SHA-256 với TTL 20 phút.
- Giao diện Frontend tại `http://localhost:3000/reset-password?token={token}` gọi API xác minh và cập nhật mật khẩu.

#### 2.2. Các Endpoints liên quan

---

#### Endpoint 2.2.1: Gửi yêu cầu khôi phục tài khoản
- **Method:** `GET`
- **Path:** `/api/auth/recover-account/{email}`
- **Quyền:** `Public`
- **Path Variable:** `email` (String, Required)

**Response Thành công (`200 OK`):**
*(Luôn trả thông báo chung, ngay cả khi email không tồn tại nhằm ngăn chặn tấn công dò quét người dùng)*
```json
{
  "status": {
    "code": 200,
    "message": "OK"
  },
  "data": "Nếu email tồn tại trên hệ thống, liên kết khôi phục tài khoản đã được gửi đến n***@gmail.com. Vui lòng kiểm tra."
}
```

**Response Thất bại / Lỗi:**
- **429 Too Many Requests — Gửi yêu cầu vượt quá giới hạn (Quá 5 lần/ngày/IP hoặc dính cooldown):**
```json
{
  "type": "about:blank",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Bạn đã vượt quá số lần yêu cầu khôi phục trong ngày. Vui lòng thử lại sau 24 giờ.",
  "errorCode": "TOO_MANY_REQUESTS"
}
```

---

#### Endpoint 2.2.2: Kiểm tra tính hợp lệ của Token đặt lại mật khẩu
- **Method:** `GET`
- **Path:** `/api/auth/validate-reset-token?token={rawToken}`
- **Quyền:** `Public`
- **Query Parameters:** `token` (String, Required)

**Response Thành công (`200 OK`):**
```json
{
  "status": {
    "code": 200,
    "message": "OK"
  },
  "data": "Token hợp lệ."
}
```

**Response Thất bại / Lỗi:**
- **401 Unauthorized — Token không hợp lệ hoặc đã hết hạn:**
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Liên kết khôi phục mật khẩu không hợp lệ hoặc đã hết hạn.",
  "errorCode": "INVALID_CREDENTIALS"
}
```

---

#### Endpoint 2.2.3: Hoàn tất đặt lại mật khẩu mới
- **Method:** `POST`
- **Path:** `/api/auth/reset-password?code={rawToken}`
- **Quyền:** `Public`
- **Query Parameters:** `code` (String, Required) — Token gửi trong email
- **Headers:** `Content-Type: application/json`

**Request Body (`AccountVerificationRequest`):**
```json
{
  "new_password": "NewSecurePassword@2026",
  "confirm_password": "NewSecurePassword@2026"
}
```

**Response Thành công (`200 OK`):**
```json
{
  "status": {
    "code": 200,
    "message": "OK"
  },
  "data": "Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại với mật khẩu mới."
}
```

**Response Thất bại / Lỗi:**
- **400 Bad Request — Mật khẩu xác nhận không khớp:**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Mật khẩu xác nhận không trùng khớp.",
  "errorCode": "INVALID_CREDENTIALS"
}
```
- **401 Unauthorized — Token không hợp lệ hoặc đã bị sử dụng:**
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Mã xác thực không hợp lệ hoặc đã hết hiệu lực.",
  "errorCode": "INVALID_CREDENTIALS"
}
```

---

### LOẠI 3: `CREDENTIAL_CHANGE` — Thay Đổi Thông Tin Đăng Nhập (Username / Password)

#### 3.1. Chức năng nghiệp vụ
- Bảo mật nâng cao: Cho phép người dùng đang đăng nhập đổi Tên đăng nhập và Mật khẩu.
- Quy trình 2 bước (Two-Phase Grant):
  1. Gửi mail chứa link kích hoạt hiệu lực **5 phút** (`http://localhost:3000/credential-change/activate?token=...`).
  2. Khi người dùng click link trên bất kỳ thiết bị nào (Cross-Device), quyền thay đổi được kích hoạt (Active Grant Session 5 phút).
  3. Trình duyệt gốc polling trạng thái thành công thì mới mở form cho phép submit đổi username / password.
  4. Thu hồi toàn bộ Refresh Token của các thiết bị khác sau khi đổi thành công.

#### 3.2. Các Endpoints liên quan

---

#### Endpoint 3.3.1: Gửi yêu cầu thay đổi thông tin đăng nhập
- **Method:** `POST`
- **Path:** `/api/auth/credential-change/request`
- **Quyền:** `Authenticated` (Cần Bearer JWT)
- **Headers:** `Authorization: Bearer <accessToken>`

**Response Thành công (`200 OK`):**
```json
{
  "status": {
    "code": 200,
    "message": "OK"
  },
  "data": "Liên kết xác thực thay đổi thông tin đăng nhập đã được gửi đến t***@example.com. Vui lòng kiểm tra hộp thư."
}
```

**Response Thất bại / Lỗi:**
- **401 Unauthorized — Chưa đăng nhập hoặc Access Token hết hạn:**
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Full authentication is required to access this resource"
}
```
- **429 Too Many Requests — Yêu cầu trước đó vẫn còn hiệu lực (Anti-Spam 5 phút):**
```json
{
  "type": "about:blank",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Yêu cầu xác thực trước đó của bạn vẫn đang có hiệu lực. Vui lòng kiểm tra hộp thư hoặc thử lại sau.",
  "errorCode": "TOO_MANY_REQUESTS"
}
```
- **400 Bad Request — Tài khoản đang bị khóa hoặc chưa Active:**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Tài khoản chưa được kích hoạt hoặc đang bị khóa.",
  "errorCode": "INVALID_CREDENTIALS"
}
```

---

#### Endpoint 3.3.2: Kích hoạt quyền đổi credentials từ Link Email (Public Cross-Device)
- **Method:** `GET`
- **Path:** `/api/auth/credential-change/activate?token={rawToken}`
- **Quyền:** `Public` (Không yêu cầu Bearer JWT)
- **Query Parameters:** `token` (String, Required)
- **Kiểu trả về:** `text/html;charset=UTF-8`

**Response Thành công (`200 OK` — HTML Page):**
Trả về giao diện web thông báo:
```html
<!DOCTYPE html>
<html>
  <body>
    <h1>Kích hoạt quyền thay đổi thành công!</h1>
    <p>Bạn có 5 phút để hoàn tất cập nhật tên đăng nhập hoặc mật khẩu trên phiên làm việc của mình.</p>
  </body>
</html>
```

**Response Thất bại (`200 OK` — HTML Error Page):**
```html
<!DOCTYPE html>
<html>
  <body>
    <h1>Kích hoạt thất bại</h1>
    <p>Token xác thực không hợp lệ hoặc đã hết hạn.</p>
  </body>
</html>
```

---

#### Endpoint 3.3.3: Polling kiểm tra trạng thái kích hoạt (In-Memory Fast Guard)
- **Method:** `GET`
- **Path:** `/api/auth/credential-change/status`
- **Quyền:** `Authenticated` (Yêu cầu Bearer JWT)
- **Headers:** `Authorization: Bearer <accessToken>`

**Response Thành công khi ĐÃ KÍCH HOẠT (`200 OK`):**
```json
{
  "status": {
    "code": 200,
    "message": "OK"
  },
  "data": {
    "status": "ACTIVE",
    "remainingSeconds": 285,
    "expiresAt": "2026-09-28T02:15:30"
  }
}
```

**Response Thất bại khi CHƯA KÍCH HOẠT hoặc HẾT HẠN (`401 Unauthorized`):**
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Chưa có quyền thay đổi thông tin đăng nhập hoặc phiên xác thực đã hết hạn.",
  "errorCode": "UNAUTHORIZED"
}
```

---

#### Endpoint 3.3.4: Thực hiện cập nhật Username & Mật khẩu
- **Method:** `PUT`
- **Path:** `/api/auth/update-credentials`
- **Quyền:** `Authenticated` (Yêu cầu Bearer JWT)
- **Headers:** 
  - `Authorization: Bearer <accessToken>`
  - `Content-Type: application/json`

**Request Body (`UpdateCredentialsRequest`):**
```json
{
  "newUsername": "new_username_2026",
  "newPassword": "NewStrongPassword@2026",
  "confirmPassword": "NewStrongPassword@2026"
}
```

**Response Thành công (`200 OK`):**
*(Hệ thống hủy Token cấp quyền, lưu username cooldown 30 ngày, và revoke toàn bộ session cũ)*
```json
{
  "status": {
    "code": 200,
    "message": "OK"
  },
  "data": "Cập nhật thông tin đăng nhập thành công. Tất cả các phiên làm việc khác đã được đăng xuất để bảo mật."
}
```

**Response Thất bại / Lỗi:**
- **401 Unauthorized — Chưa kích hoạt quyền qua link email:**
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Bạn chưa kích hoạt quyền đổi thông tin qua email hoặc phiên xác thực đã hết hạn.",
  "errorCode": "INVALID_CREDENTIALS"
}
```
- **409 Conflict — Tên đăng nhập mới đã có người sử dụng:**
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Tên đăng nhập đã tồn tại trên hệ thống. Vui lòng chọn tên khác.",
  "errorCode": "EXISTING_USER"
}
```
- **401 Unauthorized — Đổi username khi đang trong thời gian Cooldown 30 ngày:**
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Bạn chỉ được đổi tên đăng nhập tối đa 1 lần mỗi tháng. Vui lòng thử lại sau ngày 2026-10-27.",
  "errorCode": "INVALID_CREDENTIALS"
}
```

---

### LOẠI 4: `BIRTHDAY_GREETING` — Chúc Mừng Sinh Nhật & Tặng Voucher Ưu Đãi

#### 4.1. Chức năng nghiệp vụ
- Tự động chăm sóc khách hàng vào ngày sinh nhật (Customer Lifecycle Retention).
- Tặng mã voucher giảm giá cá nhân hóa kèm liên kết xem hồ sơ quà tặng (`http://localhost:3000/profile?voucher={code}`).
- Cơ chế Deduplication đặc biệt trên Redis: Khóa `BIRTHDAY:{recipient}:{year}` có TTL **365 ngày** (525.600 phút), đảm bảo mỗi khách hàng chỉ nhận đúng 1 voucher sinh nhật trong năm, bất kể cron job quét lại nhiều lần.

#### 4.2. Cơ chế kích hoạt & Endpoints kiểm thử / Xem trước

*Email này được kích hoạt thông qua Cron Job / Scheduler chạy hàng ngày gọi method:*
```java
notificationEventProducer.sendBirthdayGreetingEmail(
    recipient, username, voucherCode, discountPercent, giftUrl
);
```

Hệ thống cung cấp 2 endpoint xem trước (Preview) dành cho Quản trị viên và Kỹ thuật viên:

---

#### Endpoint 4.4.1: Xem trước Email HTML mẫu trên trình duyệt (GET)
- **Method:** `GET`
- **Path:** `/api/internal/notification/preview/BIRTHDAY_GREETING`
- **Quyền:** `Internal` / `Public in Dev`
- **Query Parameters:**
  - `username` (String, Optional, Mặc định: `"Nguyễn Văn A"`)
  - `url` (String, Optional, Mặc định: `"http://localhost:3000/profile"`)
- **Kiểu trả về:** `text/html;charset=UTF-8`

**Response Thành công (`200 OK`):**
Trả về toàn bộ mã nguồn HTML đã render (hiển thị giao diện email sinh nhật với mã voucher `BDAY-2026-PREVIEW`, ưu đãi `15%`, nút bấm liên kết về `http://localhost:3000/profile`).

**Response Thất bại (`400 Bad Request`):**
Khi truyền mã template không hợp lệ (ví dụ `/preview/UNKNOWN_TEMPLATE`):
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Mã template không hợp lệ: UNKNOWN_TEMPLATE",
  "errorCode": "INVALID_TEMPLATE"
}
```

---

#### Endpoint 4.4.2: Xem trước Email HTML tùy biến tham số (POST JSON)
- **Method:** `POST`
- **Path:** `/api/internal/notification/preview`
- **Quyền:** `Internal` / `Public in Dev`
- **Headers:** `Content-Type: application/json`

**Request Body (`EmailPreviewRequest`):**
```json
{
  "templateCode": "BIRTHDAY_GREETING",
  "variables": {
    "username": "Trần Thị B",
    "email": "tranthib@gmail.com",
    "voucherCode": "BDAY-VIP-30",
    "discountPercent": "30",
    "giftUrl": "http://localhost:3000/profile?voucher=BDAY-VIP-30"
  }
}
```

**Response Thành công (`200 OK`):**
Trả về chuỗi HTML đầy đủ đã gắn các biến tùy biến từ Request Body.

**Response Thất bại (`400 Bad Request`):**
- Khi trường `templateCode` bị trống:
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "templateCode không được để trống."
}
```

---

## 3. Bảng Tổng Hợp Mã Lỗi (Error Code Reference Matrix)

| Mã Lỗi (`errorCode`) | HTTP Status | Ý Nghĩa / Ngữ Cảnh Xảy Ra | Hướng Xử Lý Phía Client / UI |
|---|:---:|---|---|
| `INVALID_CREDENTIALS` | `400` / `401` | Mật khẩu xác nhận không khớp, token hết hạn (15m/20m/5m), hoặc token không đúng | Yêu cầu người dùng kiểm tra lại hoặc nhấn gửi lại link mới |
| `EXISTING_USER` | `400` / `409` | Email hoặc Tên đăng nhập đã có người đăng ký trong hệ thống | Đổi tên đăng nhập hoặc chuyển sang màn hình Đăng nhập |
| `USER_NOT_FOUND` | `404` | Không tìm thấy email khi yêu cầu gửi lại email xác thực | Kiểm tra lại địa chỉ email hoặc chuyển sang Đăng ký |
| `TOO_MANY_REQUESTS` | `429` | Vượt ngưỡng Rate Limit hoặc đang trong thời gian Cooldown (60s / 5 phút) | Khóa nút bấm (disable button) và hiển thị đồng hồ đếm ngược |
| `UNAUTHORIZED` | `401` | Chưa đăng nhập Bearer JWT hoặc chưa kích hoạt quyền đổi thông tin | Chuyển hướng về `/login` hoặc yêu cầu kiểm tra email kích hoạt |
| `INVALID_TEMPLATE` | `400` | Mã template gửi lên xem trước không nằm trong enum `TemplateCode` | Kiểm tra lại giá trị enum hợp lệ |

---

## 4. Tổng Kết & Đảm Bảo Chất Lượng (QA / Verification)

1. **Chuẩn hóa URL**: Toàn bộ 4 loại email hiện tại đều sinh liên kết hướng về `http://localhost:3000/...`, sẵn sàng để giao diện Frontend tiếp nhận query parameter `token` và điều hướng người dùng.
2. **Bảo toàn dữ liệu & Fail-Safe**: Các cơ chế Dead Letter Topic (DLT), chống rò rỉ key Redis, tự động nhả khóa khi SMTP lỗi đã được tích hợp hoàn chỉnh.
3. **Bộ Test Suite**: Đạt chuẩn 100% PASS trên toàn bộ **78 tests** liên quan đến IAM và Notification module.
