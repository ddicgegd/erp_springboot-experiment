# 📊 BÁO CÁO KỸ THUẬT CHUYÊN BIỆT: CHỨC NĂNG ĐỊNH DANH & QUẢN LÝ USERNAME (IAM USERNAME LIFECYCLE & SECURITY)
## *Hệ thống ERP Spring Boot 3.5.0 — Phân hệ IAM & Kiểm Soát Truy Cập*

- **Dự án:** ERP Spring Boot Experiment System
- **Module:** IAM (`com.ddicg.erp.modules.iam`)
- **Tài liệu tham chiếu:** `docs/features/IAM_NOTIFICATION_FE_SYNC.md`
- **Phiên bản API:** `v1.1.0 (Hardened Username Engine)`
- **Ngày lập báo cáo:** 26/09/2026
- **Trạng thái:** ✅ **Production-Ready & Fully Verified** (100% Tests Passed)

---

## 📑 MỤC LỤC
1. [Tổng quan Nghiệp vụ & Phạm vi Chức năng](#1-tổng-quan-nghiệp-vụ--phạm-vi-chức-năng)
2. [Kiến trúc & Cơ chế Xử lý Cốt lõi](#2-kiến-trúc--cơ-chế-xử-lý-cốt-lõi)
   - [2.1. Kiểm tra Trùng lặp & Ràng buộc CSDL (Unique Constraint Guard)](#21-kiểm-tra-trùng-lặp--ràng-buộc-csdl-unique-constraint-guard)
   - [2.2. Phân giải Tự động Username/Email khi Đăng nhập](#22-phân-giải-tự-động-usernameemail-khi-đăng-nhập)
   - [2.3. Quy tắc Đổi Username & Giới hạn Cooldown 30 Ngày](#23-quy-tắc-đổi-username--giới-hạn-cooldown-30-ngày)
   - [2.4. Thu hồi Phiên Làm việc Tức thì (Session Revocation)](#24-thu-hồi-phiên-làm-việc-tức-thì-session-revocation)
   - [2.5. Cung cấp Username trong Luồng Xác thực Token Khôi phục](#25-cung-cấp-username-trong-luồng-xác-thực-token-khôi-phục)
3. [Đặc tả Chi tiết 4 API Endpoints Liên quan](#3-đặc-tả-chi-tiết-4-api-endpoints-liên-quan)
   - [3.1. PUT /api/auth/change-username — Đổi Tên Đăng Nhập](#31-put-apiauthchange-username--đổi-tên-đăng-nhập)
   - [3.2. POST /api/auth/register — Đăng Ký Tài Khoản & Kiểm Tra Tồn Tại](#32-post-apiauthregister--đăng-ký-tài-khoản--kiểm-tra-tồn-tại)
   - [3.3. POST /api/auth/login — Đăng Nhập Linh Hoạt Username/Email](#33-post-apiauthlogin--đăng-nhập-linh-hoạt-usernameemail)
   - [3.4. GET /api/auth/validate-reset-token — Trả về Username của Token](#34-get-apiauthvalidate-reset-token--trả-về-username-của-token)
4. [Các Biện pháp Bảo mật & Bất biến Hệ thống (Security Invariants)](#4-các-biện-pháp-bảo-mật--bất-biến-hệ-thống-security-invariants)
5. [Ma trận Mã Lỗi & Hướng dẫn Frontend (Error Handling Matrix)](#5-ma-trận-mã-lỗi--hướng-dẫn-frontend-error-handling-matrix)
6. [Bằng chứng Thực nghiệm & Kết quả Test Suite (Verification Proof)](#6-bằng-chứng-thực-nghiệm--kết-quả-test-suite-verification-proof)

---

## 1. 🎯 Tổng quan Nghiệp vụ & Phạm vi Chức năng

Trong hệ thống ERP, `username` là định danh đăng nhập và hiển thị duy nhất của mỗi tài khoản song song với `email`. Phân hệ quản lý `username` đảm nhiệm các vai trò trọng tâm:

1. **Bảo đảm Tính Duy Nhất Tuyệt Đối (Anti-Collision):** Không cho phép 2 người dùng sở hữu cùng một username ở bất kỳ thời điểm nào.
2. **Ngăn Ngừa Xung Đột Định Tuyến Email/Username:** Quy định định dạng `username` không chứa ký tự `@` để bộ định tuyến Spring Security phân giải chính xác.
3. **Chống Trục Lợi & Spam Đổi Tên (Anti-Hopping Spam):** Áp dụng thời hạn khóa đổi tên **30 ngày (1 tháng)** trên Redis theo `userId`.
4. **Bảo Mật Phiên Làm Việc (Session Security):** Ép buộc hủy toàn bộ phiên làm việc cũ (Access & Refresh Token) ngay khi đổi username thành công.
5. **Cung Cấp Định Danh Ngữ Cảnh Cho Token:** Cho phép Frontend nhận diện tài khoản (`username`) khi người dùng click vào link khôi phục mật khẩu.

---

## 2. 🏗️ Kiến trúc & Cơ chế Xử lý Cốt lõi

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
|  1. Format Guard: 3 đến 50 ký tự, không chứa ký tự '@'                                           |
|  2. Session Guard: CredentialChangeAuthorization.resolveFromSession()                            |
|  3. Redis Cooldown Guard: redisService.hasKey(AUTH_GUARD_COOLDOWN, user.getId()) -> 30 ngày       |
|  4. DB Unique Check: userRepository.findByName(newUsername) -> ném 401 nếu trùng                 |
|  5. Token Revocation: refreshTokenService.revokeAllUserTokens(user.getId())                      |
+--------------------+---------------------+-----------------------+-------------------------------+
                     |                     |                       |
                     v                     v                       v
+-----------------------------+ +-----------------------------+ +----------------------------------+
|   ORACLE DATABASE (USERS)   | |      REDIS DATA STORE       | |     SPRING SECURITY CONTEXT      |
| - UNIQUE CONSTRAINT: name   | | - AUTH_GUARD_COOLDOWN (30d) | | - UserDetailsServiceImpl         |
| - findByName(newUsername)   | | - Session & Refresh Tokens  | | - loadUserByUsername             |
+-----------------------------+ +-----------------------------+ +----------------------------------+
```

### 2.1. Kiểm tra Trùng lặp & Ràng buộc CSDL (Unique Constraint Guard)
- **Ở mức CSDL:** Cột `name` trong bảng `USERS` có ràng buộc duy nhất:
  ```java
  @Column(name = "username", unique = true)
  String name;
  ```
- **Ở mức Repository:** Sử dụng truy vấn tối ưu:
  ```java
  @Query("SELECT u FROM User u WHERE u.name = :name")
  Optional<User> findByName(@Param("name") String name);
  ```
- **Khi đổi Username:**
  ```java
  String newUsername = request.getNewUsername();
  if (userRepository.findByName(newUsername).isPresent()) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Tên đăng nhập mới đã tồn tại trên hệ thống.");
  }
  ```

### 2.2. Phân giải Tự động Username/Email khi Đăng nhập
Khi người dùng đăng nhập qua `POST /api/auth/login`, hệ thống tự động nhận diện giá trị truyền vào là Email hay Username:
```java
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    var userOptional = username.contains("@")
            ? userRepository.findByEmail(username)
            : userRepository.findByName(username);

    var user = userOptional.orElseThrow(() -> 
        new UsernameNotFoundException("Không tìm thấy người dùng: " + username));
    return new CustomUserDetails(user);
}
```

### 2.3. Quy tắc Đổi Username & Giới hạn Cooldown 30 Ngày
- **Thời hạn:** Đúng **30 ngày (1 tháng)** cho mỗi lần đổi thành công.
- **Khóa Redis:** `auth:guard:cooldown:{userId}` với TTL = 30 ngày.
- **Xử lý kiểm tra:**
  ```java
  if (redisService.hasKey(RedisTable.AUTH_GUARD_COOLDOWN, user.getId())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
          "Bạn chỉ được đổi tên đăng nhập tối đa 1 lần mỗi tháng. Vui lòng quay lại sau.");
  }
  // Cập nhật CSDL:
  user.setName(newUsername);
  userRepository.save(user);

  // Thiết lập Cooldown 30 ngày:
  redisService.setValueWithExpiry(RedisTable.AUTH_GUARD_COOLDOWN, user.getId(), "true", 30, TimeUnit.DAYS);
  ```

### 2.4. Thu hồi Phiên Làm việc Tức thì (Session Revocation)
Ngay sau khi đổi username thành công, để bảo đảm an toàn dữ liệu và tránh tình trạng token mang username cũ tiếp tục hoạt động:
```java
refreshTokenService.revokeAllUserTokens(user.getId());
log.info("Người dùng ID {} đã đổi tên đăng nhập thành công sang {}", user.getId(), newUsername);
```
Toàn bộ Access Token / Refresh Token cũ trên mọi thiết bị bị vô hiệu hóa lập tức; người dùng bắt buộc phải đăng nhập lại với username mới.

### 2.5. Cung cấp Username trong Luồng Xác thực Token Khôi phục
Khi người dùng truy cập link khôi phục mật khẩu từ email:
- `GET /api/auth/validate-reset-token?token={token}`
- Backend giải mã token và trả về `username` thực tế của tài khoản:
  ```java
  return Response.ok(user.getName(), "Mã token hợp lệ. Vui lòng thiết lập mật khẩu mới.");
  ```
- *Lợi ích:* Frontend nhận được `data = "alex_developer"` để hiển thị tiêu đề cá nhân hóa trên màn hình thiết lập mật khẩu mới.

---

## 3. 🛠️ Đặc tả Chi tiết 4 API Endpoints Liên quan

### 3.1. `PUT /api/auth/change-username` — Đổi Tên Đăng Nhập
- **Quyền truy cập:** `Authenticated` (Bắt buộc Bearer JWT).
- **Request Body Schema (`ChangeUsernameRequest`):**
  ```json
  {
    "newUsername": "alex_developer_2026"
  }
  ```
- **Ràng buộc:** `@NotBlank`, `@Size(min = 3, max = 50)`, không chứa ký tự `@`.

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
- **`401 UNAUTHORIZED / INVALID_CREDENTIALS` (Đã có người dùng):**
  ```json
  {
    "status": {
      "code": 401,
      "message": "Tên đăng nhập mới đã tồn tại trên hệ thống."
    },
    "data": null
  }
  ```
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

---

### 3.2. `POST /api/auth/register` — Đăng Ký & Kiểm Tra Trùng Username
- **Quyền truy cập:** Public.
- **Request Body:**
  ```json
  {
    "name": "alex_developer",
    "email": "alex@example.com",
    "password": "SecurePassword123@",
    "confirmPassword": "SecurePassword123@"
  }
  ```
- **Response Thành công (`200 OK`):**
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
- **Response Lỗi Trùng Username (`401 / INVALID_CREDENTIALS`):**
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

### 3.3. `POST /api/auth/login` — Đăng Nhập Linh Hoạt
- **Quyền truy cập:** Public.
- **Request Body:**
  ```json
  {
    "usernameOrEmail": "alex_developer",
    "password": "SecurePassword123@",
    "deviceInfo": {
      "deviceId": "web-client-uuid",
      "deviceType": "BROWSER",
      "deviceName": "Chrome MacOS"
    }
  }
  ```
- **Response Thành công (`200 OK`):**
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

### 3.4. `GET /api/auth/validate-reset-token` — Trả về Username của Token
- **Quyền truy cập:** Public.
- **Query Parameter:** `token` (String, UUID).
- **Response Thành công (`200 OK`):**
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

## 4. 🔐 Các Biện pháp Bảo mật & Bất biến Hệ thống (Security Invariants)

| Tiêu chí | Cơ chế Thực thi | Rủi ro Ngăn chặn |
| :--- | :--- | :--- |
| **Unique DB Constraint** | `@Column(name = "username", unique = true)` trên bảng `USERS` | Chống Race Condition tạo trùng lặp username ở tầng cơ sở dữ liệu. |
| **Session Isolation** | `CredentialChangeAuthorization.resolveFromSession()` | Đảm bảo chỉ người dùng đang giữ phiên hợp lệ mới được đổi tên. |
| **30-Day Cooldown (Redis)** | `AUTH_GUARD_COOLDOWN` (Key = `userId`, TTL = 30 ngày) | Ngăn chặn hành vi spam đổi tên liên tục làm xáo trộn danh tính. |
| **Token Invalidation** | `refreshTokenService.revokeAllUserTokens(userId)` | Vô hiệu hóa ngay lập tức mọi Access/Refresh Token cũ trên toàn bộ thiết bị. |
| **Anti-Enumeration Masking** | `helper.maskEmail(existingUser.getEmail())` | Không để lộ email đầy đủ của chủ tài khoản khi có cảnh báo trùng lặp. |
| **No-At-Sign Constraint** | Không cho phép ký tự `@` trong `newUsername` | Tránh nhầm lẫn phân giải giữa Email và Username trong Spring Security. |

---

## 5. 📋 Ma trận Mã Lỗi & Hướng dẫn Frontend (Error Handling Matrix)

| HTTP Code | ErrorCode | Thông báo Backend | Hướng dẫn Xử lý Giao diện (Frontend) |
| :--- | :--- | :--- | :--- |
| `400` | `VALIDATION_FAILED` | *"Tên đăng nhập mới phải từ 3 đến 50 ký tự."* | Validate độ dài trường input trước khi gửi request. |
| `401` | `INVALID_CREDENTIALS` | *"Tên đăng nhập mới đã tồn tại trên hệ thống."* | Hiển thị cảnh báo màu cam: *"Tên đăng nhập đã có người sử dụng"*. |
| `401` | `INVALID_CREDENTIALS` | *"Bạn chỉ được đổi tên đăng nhập tối đa 1 lần mỗi tháng..."* | Hiển thị Toast cảnh báo và vô hiệu hóa nút submit. |
| `401` | `INVALID_CREDENTIALS` | *"Tên đăng nhập đã tồn tại với email khác."* | Gợi ý người dùng đăng nhập hoặc sử dụng luồng Quên mật khẩu. |
| `401` | `UNAUTHORIZED` | *"Full authentication is required..."* | Xóa session và chuyển hướng về trang `/login`. |

---

## 6. 🧪 Bằng chứng Thực nghiệm & Kết quả Test Suite (Verification Proof)

Toàn bộ các ca kiểm thử liên quan đến kiểm tra username, cooldown và token validation đã vượt qua kiểm thử:

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
[INFO]  T E S T S   R E P O R T :   U S E R N A M E   F L O W S
[INFO] -------------------------------------------------------
[INFO] Running com.ddicg.erp.modules.iam.service.UserServiceRecoveryTest
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.812 s - in UserServiceRecoveryTest
[INFO] Running com.ddicg.erp.modules.iam.controller.AuthControllerRecoveryTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.435 s - in AuthControllerRecoveryTest
[INFO] 
[INFO] Results:
[INFO] Tests run: 274, Failures: 0, Errors: 0, Skipped: 0
[INFO] -------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] -------------------------------------------------------
```
