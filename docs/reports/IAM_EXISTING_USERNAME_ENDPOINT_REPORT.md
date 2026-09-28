# 📊 BÁO CÁO KỸ THUẬT & ĐẶC TẢ CHỨC NĂNG: KIỂM SOÁT ĐỊNH DANH & KIỂM TRA SỰ TỒN TẠI CỦA USERNAME (IAM USERNAME EXISTENCE & LIFECYCLE)
## *Hệ thống ERP Spring Boot 3.5.0 — Phân hệ IAM & Quản lý Phiên Người Dùng*

- **Dự án:** ERP Spring Boot Experiment System
- **Module:** IAM (`com.ddicg.erp.modules.iam`)
- **Tài liệu liên quan:** `docs/features/IAM_NOTIFICATION_FE_SYNC.md`, `docs/reports/CART_API_EXECUTION_REPORT.md`
- **Phiên bản:** `v1.1.0`
- **Ngày lập báo cáo:** 26/09/2026
- **Trạng thái:** ✅ **Production-Ready & Fully Verified** (100% Tests Passed)

---

## 📑 MỤC LỤC
1. [Bối cảnh Nghiệp vụ & Mục tiêu Kỹ thuật](#1-bối-cảnh-nghiệp-vụ--mục-tiêu-kỹ-thuật)
2. [Kiến trúc & Cơ chế Vận hành Cốt lõi](#2-kiến-trúc--cơ-chế-vận-hành-cốt-lõi)
   - [2.1. Cơ chế Kiểm tra Sự Tồn tại của Username (Existence Guard)](#21-cơ-chế-kiểm-tra-sự-tồn-tại-của-username-existence-guard)
   - [2.2. Cơ chế Phân giải Kép (Username/Email Dual-Resolution)](#22-cơ-chế-phân-giải-kép-usernameemail-dual-resolution)
   - [2.3. Cung cấp Username Thực tế trong Luồng Xác thực Token](#23-cung-cấp-username-thực-tế-trong-luồng-xác-thực-token)
   - [2.4. Đổi Username với Cooldown 30 Ngày & Session Revocation](#24-đổi-username-với-cooldown-30-ngày--session-revocation)
3. [Đặc tả Chi tiết Các Endpoint Liên quan](#3-đặc-tả-chi-tiết-các-endpoint-liên-quan)
   - [3.1. PUT /api/auth/change-username — Đổi Tên Đăng Nhập & Kiểm Tra Tồn Tại](#31-put-apiauthchange-username--đổi-tên-đăng-nhập--kiểm-tra-tồn-tại)
   - [3.2. GET /api/auth/validate-reset-token — Trả về Username của Tài khoản](#32-get-apiauthvalidate-reset-token--trả-về-username-của-tài-khoản)
   - [3.3. POST /api/auth/register — Bảo vệ Tính Duy nhất của Username](#33-post-apiauthregister--bảo-vệ-tính-duy-nhất-của-username)
   - [3.4. POST /api/auth/login — Nhận diện Tự động Username/Email](#34-post-apiauthlogin--nhận-diện-tự-động-usernameemail)
4. [Các Biện pháp Bảo mật & Bất biến Hệ thống (Security Invariants)](#4-các-biện-pháp-bảo-mật--bất-biến-hệ-thống-security-invariants)
5. [Ma trận Mã Lỗi RESTful & Hướng dẫn Frontend (Error Matrix)](#5-ma-trận-mã-lỗi-restful--hướng-dẫn-frontend-error-matrix)
6. [Bằng chứng Thực nghiệm & Kết quả Kiểm thử Tự động](#6-bằng-chứng-thực-nghiệm--kết-quả-kiểm-thử-tự-động)

---

## 1. 🎯 Bối cảnh Nghiệp vụ & Mục tiêu Kỹ thuật

Trong hệ thống ERP đa dịch vụ, `username` đóng vai trò là định danh hiển thị và phương thức đăng nhập chính song song với `email`. Việc kiểm tra sự tồn tại (existence check) và quản lý vòng đời của `username` đối mặt với các thách thức lớn:
1. **Xung đột định danh (Identity Collision):** Cho phép hai người dùng cùng tạo hoặc đổi sang cùng một tên đăng nhập dẫn đến sai lệch dữ liệu toàn hệ thống.
2. **Nhầm lẫn giữa Email và Username:** Nếu `username` chứa ký tự `@`, bộ định tuyến xác thực (`UserDetailsService`) sẽ nhận diện nhầm là email, làm gián đoạn luồng đăng nhập.
3. **Lạm dụng đổi định danh (Username Hopping Spam):** Người dùng liên tục đổi username để né tránh quản trị hoặc chiếm dụng tên đẹp.
4. **Trải nghiệm xác thực Token:** Khi người dùng mở liên kết khôi phục mật khẩu hoặc kích hoạt, Frontend cần biết chính xác tài khoản đang được thao tác thuộc về `username` nào để hiển thị thông tin ngữ cảnh thân thiện thay vì hiển thị form ẩn danh.

### Mục tiêu kỹ thuật đạt được:
- ✅ **Bảo đảm 100% tính duy nhất**: Kiểm tra tồn tại qua `userRepository.findByName(...)` trên Oracle Database với ràng buộc `@Column(unique = true)`.
- ✅ **Phân tách ngữ cảnh định danh**: Bắt buộc định dạng `username` không chứa `@`, phân giải tự động `isEmailFormat()`.
- ✅ **Bảo vệ bằng Redis Cooldown**: Giới hạn đổi username **30 ngày/lần** gắn với `user.id`.
- ✅ **Thu hồi phiên tức thì (Zero Session Ghost)**: Ngay sau khi đổi username, toàn bộ Access Token và Refresh Token cũ trên mọi thiết bị bị hủy lập tức qua `refreshTokenService.revokeAllUserTokens(user.getId())`.
- ✅ **Cung cấp ngữ cảnh Username trong Token Validation**: Endpoint `GET /api/auth/validate-reset-token` trả về `username` trực tiếp trong `Response<String>` data.

---

## 2. 🏗️ Kiến trúc & Cơ chế Vận hành Cốt lõi

```
+--------------------------------------------------------------------------------------------------+
|                                    CLIENT (Web FE / Mobile App)                                  |
+---------------------+--------------------+-----------------------+-------------------------------+
                      |                    |                       |
                      | POST /register     | PUT /change-username  | GET /validate-reset-token
                      v                    v                       v
+--------------------------------------------------------------------------------------------------+
|                                      AUTH CONTROLLER (IAM API)                                   |
+---------------------+--------------------+-----------------------+-------------------------------+
                      |                    |                       |
                      v                    v                       v
+--------------------------------------------------------------------------------------------------+
|                                        USER SERVICE                                              |
|  1. Regex & Format Guard (Không chứa '@', 3-50 ký tự)                                            |
|  2. Session Guard (CredentialChangeAuthorization.resolveFromSession)                             |
|  3. Redis Cooldown Check (AUTH_GUARD_COOLDOWN: key=userId, TTL=30 days)                          |
|  4. Database Existence Query: userRepository.findByName(username)                                |
|  5. Token Revocation & Audit Logging                                                             |
+--------------------+---------------------+-----------------------+-------------------------------+
                     |                     |                       |
                     v                     v                       v
+-----------------------------+ +-----------------------------+ +----------------------------------+
|   ORACLE DATABASE (JPA)     | |      REDIS DATA STORE       | |     NOTIFICATION / EVENT BUS     |
| - UNIQUE CONSTRAINT: name   | | - AUTH_GUARD_COOLDOWN (30d) | | - AccountRecoveryEvent           |
| - findByName(newUsername)   | | - AUTH_RECOVERY_TOKEN (20m) | | - VerificationEmailEvent         |
| - findByNameAndEmail(...)   | | - Session & Refresh Tokens  | |                                  |
+-----------------------------+ +-----------------------------+ +----------------------------------+
```

### 2.1. Cơ chế Kiểm tra Sự Tồn tại của Username (Existence Guard)
Hệ thống sử dụng phương thức truy vấn tối ưu tại tầng JPA Repository:
```java
@Query("SELECT u FROM User u WHERE u.name = :name")
Optional<User> findByName(@Param("name") String name);
```
- Khi thực hiện đổi username:
  ```java
  String newUsername = request.getNewUsername();
  if (userRepository.findByName(newUsername).isPresent()) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Tên đăng nhập mới đã tồn tại trên hệ thống.");
  }
  ```
- Khi đăng ký tài khoản mới: Nếu tên đăng nhập đã tồn tại nhưng thuộc về email khác, hệ thống chặn ngay và che giấu email đối phương để chống lộ dữ liệu (`helper.maskEmail(...)`).

### 2.2. Cơ chế Phân giải Kép (Username/Email Dual-Resolution)
Tại tầng xác thực đăng nhập (`POST /api/auth/login`) và `UserDetailsService`:
```java
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    var userOptional = username.contains("@")
            ? userRepository.findByEmail(username)
            : userRepository.findByName(username);

    var user = userOptional.orElseThrow(() -> 
        new UsernameNotFoundException("Không tìm thấy người dùng: " + username));
    // Tạo CustomUserDetails...
}
```

### 2.3. Cung cấp Username Thực tế trong Luồng Xác thực Token
Khi người dùng truy cập liên kết kích hoạt / đặt lại mật khẩu từ email:
- Endpoint: `GET /api/auth/validate-reset-token?token={token}`
- Backend giải mã token từ Redis (`AUTH_RECOVERY_TOKEN`), tìm ra `User` tương ứng và trả về `user.getName()`:
```java
@Override
@Transactional(readOnly = true)
public Response<String> validateResetToken(@NonNull final String token) {
    var recoveryToken = accountRecoveryService.resolve(token);
    User user = recoveryToken.user();
    
    // Nếu user ở trạng thái INACTIVE -> Kích hoạt luôn
    if (user.getStatus() == ActiveStatus.INACTIVE) {
        user.setStatus(ActiveStatus.ACTIVE);
        userRepository.save(user);
    }
    
    return Response.ok(user.getName(), "Mã token hợp lệ. Vui lòng thiết lập mật khẩu mới.");
}
```
*Lợi ích:* Frontend nhận được chính xác tên đăng nhập (`data = "nguyenvana"`) để hiển thị tiêu đề cá nhân hóa trên trang Đặt lại mật khẩu.

### 2.4. Đổi Username với Cooldown 30 Ngày & Session Revocation
- **Định danh theo `userId`:** Cooldown được lưu trong Redis theo `RedisTable.AUTH_GUARD_COOLDOWN` với key là `user.getId()`, tránh việc người dùng đổi email để bypass cooldown.
- **Hủy phiên toàn diện:** Sau khi `user.setName(newUsername)` được lưu vào cơ sở dữ liệu, `refreshTokenService.revokeAllUserTokens(user.getId())` được kích hoạt để loại bỏ toàn bộ phiên làm việc của username cũ.

---

## 3. 🛠️ Đặc tả Chi tiết Các Endpoint Liên quan

### 3.1. `PUT /api/auth/change-username` — Đổi Tên Đăng Nhập & Kiểm Tra Tồn Tại

Thay đổi tên đăng nhập của người dùng hiện tại đang đăng nhập.

- **Method:** `PUT`
- **Path:** `/api/auth/change-username`
- **Authentication:** **Bắt buộc** (`Authorization: Bearer <accessToken>`)
- **Content-Type:** `application/json`

#### Request Body Schema (`ChangeUsernameRequest`)
| Thuộc tính | Kiểu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `newUsername` | String | `@NotBlank`, `@Size(min = 3, max = 50)` | Tên đăng nhập mới mong muốn |

#### Request Body Ví dụ:
```json
{
  "newUsername": "john_doe_2026"
}
```

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

#### Phản hồi Lỗi Thường gặp:
- **`401 UNAUTHORIZED` / `INVALID_CREDENTIALS` (Đã tồn tại username):**
  ```json
  {
    "status": {
      "code": 401,
      "message": "Tên đăng nhập mới đã tồn tại trên hệ thống."
    },
    "data": null
  }
  ```
- **`401 UNAUTHORIZED` / `INVALID_CREDENTIALS` (Đang trong Cooldown 30 ngày):**
  ```json
  {
    "status": {
      "code": 401,
      "message": "Bạn chỉ được đổi tên đăng nhập tối đa 1 lần mỗi tháng. Vui lòng quay lại sau."
    },
    "data": null
  }
  ```
- **`400 BAD_REQUEST` / `VALIDATION_FAILED` (Dưới 3 ký tự hoặc để trống):**
  ```json
  {
    "status": {
      "code": 400,
      "message": "Tên đăng nhập mới phải từ 3 đến 50 ký tự."
    },
    "data": null
  }
  ```

---

### 3.2. `GET /api/auth/validate-reset-token` — Trả về Username của Tài khoản

Xác thực tính hợp lệ của token khôi phục và trả về `username` tương ứng của chủ sở hữu token.

- **Method:** `GET`
- **Path:** `/api/auth/validate-reset-token`
- **Authentication:** Public (Không yêu cầu đăng nhập)
- **Query Parameter:** `token` (String, Required) - Mã token UUID từ email.

#### Request Ví dụ:
```http
GET /api/auth/validate-reset-token?token=9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d HTTP/1.1
Host: localhost:8080
```

#### Response Thành công (`200 OK`):
```json
{
  "status": {
    "code": 200,
    "message": "Mã token hợp lệ. Vui lòng thiết lập mật khẩu mới."
  },
  "data": "alex_developer"
}
```

---

### 3.3. `POST /api/auth/register` — Bảo vệ Tính Duy nhất của Username

Đăng ký tài khoản người dùng mới và kiểm tra tính duy nhất của username.

- **Method:** `POST`
- **Path:** `/api/auth/register`
- **Authentication:** Public

#### Request Body Schema:
```json
{
  "name": "alex_developer",
  "email": "alex@example.com",
  "password": "SecurePassword123@",
  "confirmPassword": "SecurePassword123@"
}
```

#### Response Thành công (`200 OK`):
```json
{
  "status": {
    "code": 200,
    "message": "Đăng ký tài khoản thành công. Vui lòng kiểm tra email để kích hoạt tài khoản."
  },
  "data": {
    "message": "Đăng ký thành công",
    "username": "alex_developer",
    "email": "alex@example.com"
  }
}
```

#### Response Lỗi Trùng Username (`401 / INVALID_CREDENTIALS`):
```json
{
  "status": {
    "code": 401,
    "message": "Tên đăng nhập đã tồn tại với email khác."
  },
  "data": {
    "email": "a***@example.com"
  }
}
```

---

### 3.4. `POST /api/auth/login` — Nhận diện Tự động Username/Email

Cho phép đăng nhập linh hoạt bằng `username` hoặc `email`.

- **Method:** `POST`
- **Path:** `/api/auth/login`
- **Request Body:**
```json
{
  "usernameOrEmail": "alex_developer",
  "password": "SecurePassword123@",
  "deviceInfo": {
    "deviceId": "web-chrome-v120",
    "deviceType": "BROWSER",
    "deviceName": "Chrome MacOS"
  }
}
```

#### Response Thành công (`200 OK`):
```json
{
  "status": {
    "code": 200,
    "message": "Đăng nhập thành công."
  },
  "data": {
    "username": "alex_developer",
    "email": "alex@example.com",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "d7a4e6...",
    "avatarUrl": "https://s3.ddicg.com/avatars/user-1.png",
    "gender": "MALE"
  }
}
```

---

## 4. 🔐 Các Biện pháp Bảo mật & Bất biến Hệ thống (Security Invariants)

| Biện pháp | Cơ chế Thực thi | Mục đích Ngăn chặn |
| :--- | :--- | :--- |
| **Unique DB Constraint** | `@Column(name = "username", unique = true)` trên bảng `USERS` | Chống Race Condition tạo trùng username ở mức DB |
| **Session Authentication Guard** | `CredentialChangeAuthorization.resolveFromSession()` | Đảm bảo chỉ người dùng sở hữu phiên mới được đổi username |
| **30-Day Cooldown on Redis** | `AUTH_GUARD_COOLDOWN` theo key `user.getId()` | Ngăn chặn hành vi spam đổi tên liên tục làm xáo trộn danh tính |
| **Token Invalidation** | `refreshTokenService.revokeAllUserTokens(user.getId())` | Vô hiệu hóa ngay lập tức các phiên đăng nhập cũ trên mọi thiết bị |
| **Anti-Enumeration Protection** | Masking email `helper.maskEmail(...)` khi thông báo xung đột | Chống rò rỉ địa chỉ email đầy đủ của chủ sở hữu username trước |
| **Format Strictness** | Tên đăng nhập từ 3 đến 50 ký tự, không chứa `@` | Tránh xung đột luồng phân giải `UserDetailsService` |

---

## 5. 📋 Ma trận Mã Lỗi RESTful & Hướng dẫn Frontend (Error Matrix)

| HTTP Code | ErrorCode (`core`) | Thông báo Lỗi Hệ thống | Hướng dẫn Xử lý Giao diện (Frontend UX) |
| :--- | :--- | :--- | :--- |
| **`400`** | `VALIDATION_FAILED` | *"Tên đăng nhập mới phải từ 3 đến 50 ký tự."* | Validate form phía Client trước khi gửi request (đếm độ dài ký tự). |
| **`400`** | `VALIDATION_FAILED` | *"Tên đăng nhập mới không được để trống"* | Báo đỏ ô nhập liệu khi người dùng để trống form. |
| **`401`** | `INVALID_CREDENTIALS` | *"Tên đăng nhập mới đã tồn tại trên hệ thống."* | Hiển thị cảnh báo màu cam dưới ô input: *"Tên đăng nhập đã có người sử dụng"*. |
| **`401`** | `INVALID_CREDENTIALS` | *"Bạn chỉ được đổi tên đăng nhập tối đa 1 lần mỗi tháng..."* | Hiển thị Toast thông báo và disable nút Đổi tên nếu chưa đủ 30 ngày. |
| **`401`** | `INVALID_CREDENTIALS` | *"Tên đăng nhập đã tồn tại với email khác."* | Hiển thị gợi ý đăng nhập hoặc sử dụng tính năng Quên mật khẩu. |
| **`401`** | `UNAUTHORIZED` | *"Full authentication is required to access this resource"* | Chuyển hướng người dùng về trang `/login` do hết hạn Access Token. |

---

## 6. 🧪 Bằng chứng Thực nghiệm & Kết quả Kiểm thử Tự động

Tất cả các luồng kiểm tra username và các endpoint liên quan đã được kiểm thử toàn diện qua bộ Unit & Integration Test suites:

### Danh sách Test Cases Trọng tâm (`UserServiceRecoveryTest` & `AuthControllerUpdateTest`):

1. **`changeUsername_WhenUsernameExists_ShouldThrowInvalidCredentials`**:
   - *Kịch bản:* User gửi `newUsername = "already_taken"`, Mock `userRepository.findByName("already_taken")` trả về User có sẵn.
   - *Kết quả:* Ném `BusinessException(ErrorCode.INVALID_CREDENTIALS)` với thông điệp *"Tên đăng nhập mới đã tồn tại trên hệ thống."*.
2. **`changeUsername_WhenWithinCooldown_ShouldThrowInvalidCredentials`**:
   - *Kịch bản:* User đã đổi username trong vòng 30 ngày (`AUTH_GUARD_COOLDOWN` còn tồn tại trên Redis).
   - *Kết quả:* Chặn thao tác ngay từ bước kiểm tra Redis Cooldown.
3. **`changeUsername_WhenValid_ShouldSucceedAndRevokeTokens`**:
   - *Kịch bản:* Tên đăng nhập hợp lệ, chưa ai sử dụng, hết thời gian cooldown.
   - *Kết quả:* Lưu username mới vào Oracle DB, thiết lập key Cooldown 30 ngày trên Redis, gọi `revokeAllUserTokens` thành công.
4. **`validateResetToken_ShouldReturnActualUsername`**:
   - *Kịch bản:* Client gửi token hợp lệ lên `GET /api/auth/validate-reset-token`.
   - *Kết quả:* Trả về `Response<String>` với HTTP 200 OK và `data` chính là `user.getName()`.
5. **`register_WhenUsernameExistsWithDifferentEmail_ShouldThrow`**:
   - *Kịch bản:* Đăng ký username đã tồn tại của người khác.
   - *Kết quả:* Chặn đăng ký và trả về email được mask ẩn danh.

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S   R E P O R T :   I A M   M O D U L E
[INFO] -------------------------------------------------------
[INFO] Running com.ddicg.erp.modules.iam.service.UserServiceRecoveryTest
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.812 s - in UserServiceRecoveryTest
[INFO] Running com.ddicg.erp.modules.iam.controller.AuthControllerRecoveryTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.435 s - in AuthControllerRecoveryTest
[INFO] 
[INFO] Results:
[INFO] Tests run: 293, Failures: 0, Errors: 0, Skipped: 0
[INFO] -------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] -------------------------------------------------------
```

---

## 7. 📌 Kết luận & Khuyến nghị Triển khai Frontend

1. **Kiểm tra tức thời (Debounced Live Check):** Khuyến nghị FE có thể tận dụng hoặc debounce các thao tác xác thực form để người dùng nhận biết ngay khi tên đăng nhập bị trùng.
2. **Xử lý sau khi đổi Username thành công:** FE bắt buộc xóa sạch `accessToken` và `refreshToken` khỏi `localStorage` / `sessionStorage` / Cookies và điều hướng về trang `/login` kèm thông báo thành công.
3. **Hiển thị thông tin trên trang Reset Password:** Đọc trường `data` từ `GET /api/auth/validate-reset-token` để hiển thị: *"Thiết lập lại mật khẩu cho tài khoản: **{{data}}**"*.
