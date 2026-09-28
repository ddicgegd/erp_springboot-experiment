# 🎂 ĐẶC TẢ TÍNH NĂNG & KIẾN TRÚC: PHÂN HỆ SINH NHẬT NGƯỜI DÙNG (USER BIRTHDAY & CELEBRATION ENGINE)
## *Hệ thống ERP Spring Boot 3.5.0 — Module IAM, Notification & Tiered Perks*

- **Dự án:** ERP Spring Boot Experiment System
- **Module:** IAM (`com.ddicg.erp.modules.iam`), Notification (`com.ddicg.erp.modules.notification`)
- **Tài liệu liên quan:** `docs/reports/IAM_EXISTING_USERNAME_ENDPOINT_REPORT.md`, `docs/features/IAM_NOTIFICATION_FE_SYNC.md`
- **Phiên bản:** `v1.2.0 (Birthday Celebration & Anti-Fraud Engine)`
- **Ngày hoàn thiện:** 26/09/2026
- **Trạng thái:** ✅ **Implemented & Verified** (274/274 Tests Passed)

---

## 1. 🎯 BỐI CẢNH & VẤN ĐỀ ĐÃ KHẮC PHỤC (PROBLEM STATEMENT)

| Vấn đề cũ | Hệ quả kỹ thuật / rủi ro | Giải pháp nâng cấp chuẩn hóa |
| :--- | :--- | :--- |
| **Dùng `java.util.Date`** | Lỗi lệch ngày do múi giờ (UTC+7 vs UTC+0) làm người dùng bị sai ngày sinh 1 ngày. | Chuyển dịch toàn diện sang **`java.time.LocalDate`** với format chuẩn `yyyy-MM-dd`. |
| **Không ràng buộc Validation** | Cho phép ngày sinh ở tương lai (2099) hoặc năm sinh âm. | Áp dụng `@Past` kết hợp ràng buộc độ tuổi hợp lý (từ 10 đến 120 tuổi). |
| **Lỗ hổng trục lợi (Birthday Hopping)** | Người dùng liên tục đổi ngày sinh thành ngày hôm nay để nhận quà/voucher vô hạn. | Áp dụng **365-Day Cooldown** (`dob_updated_at`): Chỉ cho phép đổi ngày sinh **tối đa 1 lần/năm**. |
| **Thiếu ngữ cảnh cho Frontend** | Frontend phải tự tính toán ngày sinh nhật hôm nay / tháng này. | `MyProfileResponse` bổ sung: `isBirthdayToday`, `isBirthdayMonth`, `daysUntilBirthday`. |
| **Quyền riêng tư (Privacy)** | Chưa hỗ trợ ẩn năm sinh trên hồ sơ công khai. | Bổ sung trường `hideBirthYear: boolean` cho phép người dùng ẩn năm sinh. |
| **Email tri ân & Quà tặng** | Chưa có template email chúc mừng sinh nhật. | Bổ sung `TemplateCode.BIRTHDAY_GREETING` và mẫu email Bevel `birthday-email.html`. |

---

## 2. 🏗️ KIẾN TRÚC VẬN HÀNH

```
+--------------------------------------------------------------------------------------------------+
|                                    CLIENT (Web FE / Mobile App)                                  |
+-------------------------------------------------+------------------------------------------------+
                                                  |
                     PUT /api/auth/me             |  GET /api/auth/me
                     (Cập nhật ngày sinh)        |  (Lấy profile + cờ sinh nhật)
                                                  v
+--------------------------------------------------------------------------------------------------+
|                                        USER SERVICE (IAM)                                        |
|  1. Parse & Format Validation: LocalDate + @Past                                                 |
|  2. Age Guard: 10 <= Age <= 120                                                                  |
|  3. Cooldown Guard: dobUpdatedAt != null && dobUpdatedAt > now - 365 days -> HTTP 401           |
|  4. Birthday Projection Calculator:                                                              |
|     - isBirthdayToday = (dob.month == today.month && dob.day == today.day)                       |
|     - isBirthdayMonth = (dob.month == today.month)                                               |
|     - daysUntilBirthday = ChronoUnit.DAYS.between(today, nextBirthday)                           |
+-------------------------------------------------+------------------------------------------------+
                                                  |
                                                  v
+------------------------------------+   +---------------------------------------------------------+
|       ORACLE DATABASE (USERS)      |   |             NOTIFICATION PRODUCER (Kafka Event)         |
| - date_of_birth: DATE / LocalDate  |   | - sendBirthdayGreetingEmail(...)                        |
| - dob_updated_at: TIMESTAMP        |   | - Template: mail/birthday-email.html (Bevel UI)         |
| - hide_birth_year: NUMBER(1)       |   | - Deduplication: BIRTHDAY:{email}:{year}                |
+------------------------------------+   +---------------------------------------------------------+
```

---

## 3. 🛠️ API CONTRACTS CHI TIẾT

### 3.1. `GET /api/auth/me` — Lấy Hồ Sơ & Cờ Trạng Thái Sinh Nhật
- **Method:** `GET`
- **Path:** `/api/auth/me`
- **Authentication:** `Bearer JWT`
- **Response `200 OK` Mẫu:**
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
    "canChangeUsername": false,
    "daysUntilUsernameChange": 18,
    "canChangeDateOfBirth": false,
    "daysUntilDobChange": 240,
    "dateOfBirth": "2000-09-26",
    "hideBirthYear": false,
    "isBirthdayToday": true,
    "isBirthdayMonth": true,
    "daysUntilBirthday": 0,
    "avatarUrl": "https://s3.ddicg.com/avatars/u100.png",
    "gender": "MALE",
    "rank": "PLATINUM",
    "status": "ACTIVE",
    "roles": ["ROLE_USER"]
  }
}
```

---

### 3.2. `PUT /api/auth/me` — Cập Nhật Ngày Sinh & Cài Đặt Riêng Tư
- **Method:** `PUT`
- **Path:** `/api/auth/me`
- **Authentication:** `Bearer JWT`
- **Request Body Mẫu:**
```json
{
  "fullName": "Alex Developer",
  "phoneNumber": "0901234567",
  "dateOfBirth": "2000-09-26",
  "hideBirthYear": true,
  "gender": "MALE"
}
```
- **Phản hồi Lỗi Thường gặp:**
  - `400 BAD_REQUEST`: *"Ngày sinh phải là một ngày trong quá khứ"* hoặc *"Ngày sinh không hợp lệ hoặc độ tuổi phải từ 10 đến 120 tuổi."*
  - `401 UNAUTHORIZED / INVALID_CREDENTIALS`: *"Bạn chỉ được phép cập nhật ngày sinh tối đa 1 lần mỗi năm. Vui lòng liên hệ CSKH nếu cần hỗ trợ."*

---

## 4. 💌 EMAIL TRI ÂN & QUÀ TẶNG SINH NHẬT (NOTIFICATION INTEGRATION)

- **Mẫu Email:** `src/main/java/com/ddicg/erp/modules/notification/resources/templates/mail/birthday-email.html`
- **Tính năng nổi bật:**
  1. Giao diện sang trọng **Shadcn Optical Bevel** với gam màu Vàng/Hổ phách (Amber/Gold Festive Palette).
  2. Hộp quà tặng sinh nhật (Voucher Card) hiển thị mức giảm giá theo Hạng thành viên.
  3. Nút sao chép mã Voucher (Interactive Copy-to-Clipboard) với animation tích xanh mượt mà.
  4. Nút bấm Physical Bevel dẫn link trực tiếp vào trang quà tặng `/profile`.
  5. Deduplication Key chống spam: `BIRTHDAY:{recipient}:{currentYear}`.

---

## 5. 🧪 KẾT QUẢ KIỂM THỬ (TEST VERIFICATION)

Bộ test `UserServiceBirthdayTest` đã kiểm thử toàn diện các trường hợp:
1. `getMyProfile_WhenBirthdayIsToday_ShouldReturnBirthdayFlagsTrue`: ✅ **Passed**
2. `getMyProfile_WhenBirthdayIsThisMonth_ShouldReturnIsBirthdayMonthTrueAndTodayFalse`: ✅ **Passed**
3. `updateMyProfile_WhenFirstTimeUpdatingDob_ShouldSucceed`: ✅ **Passed**
4. `updateMyProfile_WhenAgeLessThan10_ShouldThrowValidationFailed`: ✅ **Passed**
5. `updateMyProfile_WhenAgeGreaterThan120_ShouldThrowValidationFailed`: ✅ **Passed**
6. `updateMyProfile_WhenUpdatingWithinCooldown_ShouldThrowInvalidCredentials`: ✅ **Passed**
7. `updateMyProfile_WhenUpdatingAfterCooldown_ShouldSucceed`: ✅ **Passed**

**Tổng kết Test Suite:** `274/274 tests passed (100%)`.
