# 📊 BÁO CÁO KỸ THUẬT & ĐẶC TẢ HOÀN THIỆN: PHÂN HỆ SINH NHẬT NGƯỜI DÙNG (USER BIRTHDAY & CELEBRATION ENGINE)
## *Hệ thống ERP Spring Boot 3.5.0 — Phân hệ IAM, Database Flyway V19 & Notification Service*

- **Dự án:** ERP Spring Boot Experiment System
- **Module:** IAM (`com.ddicg.erp.modules.iam`), Notification (`com.ddicg.erp.modules.notification`)
- **Tài liệu tham chiếu:** `docs/decisions/ADR-001-user-birthday-and-lifecycle-management.md`, `docs/features/USER_BIRTHDAY_FEATURES_SPEC.md`, `docs/reports/IAM_EXISTING_USERNAME_ENDPOINT_REPORT.md`
- **Phiên bản:** `v1.2.0 (Birthday Celebration & Anti-Fraud Engine)`
- **Ngày hoàn thành:** 26/09/2026
- **Trạng thái:** ✅ **Production-Ready & Fully Verified** (100% Tests Passed — Oracle 21c Migrated)

---

## 📑 MỤC LỤC
1. [Tóm tắt Điều hành (Executive Summary)](#1-tóm-tắt-điều-hành-executive-summary)
2. [Chẩn đoán Vấn đề & Nguyên nhân Gốc rễ (Root Cause Analysis)](#2-chẩn-đoán-vấn-đề--nguyên-nhân-gốc-rễ-root-cause-analysis)
   - [2.1. Lỗi Lệch Múi Giờ và Nợ Kỹ Thuật `java.util.Date`](#21-lỗi-lệch-múi-giờ-và-nợ-kỹ-thuật-javautildate)
   - [2.2. Lỗi Xung đột Schema Validation Hibernate 6 với Oracle DB](#22-lỗi-xung-đột-schema-validation-hibernate-6-với-oracle-db)
   - [2.3. Lỗ hổng Trục lợi Ngày Sinh (Birthday Hopping / Fraud)](#23-lỗ-hổng-trục-lợi-ngày-sinh-birthday-hopping--fraud)
3. [Kiến trúc & Cơ chế Vận hành Toàn diện](#3-kiến-trúc--cơ-chế-vận-hành-toàn-diện)
4. [Đặc tả CSDL & Flyway Migration V19](#4-đặc-tả-csdl--flyway-migration-v19)
5. [Đặc tả Chi tiết API Endpoints & Request/Response Contracts](#5-đặc-tả-chi-tiết-api-endpoints--requestresponse-contracts)
   - [5.1. GET /api/auth/me — Lấy Profile & Các Cờ Trạng thái Sinh nhật](#51-get-apiauthme--lấy-profile--các-cờ-trạng-thái-sinh-nhật)
   - [5.2. PUT /api/auth/me — Cập nhật Ngày sinh, Tuổi & Quyền Riêng tư](#52-put-apiauthme--cập-nhật-ngày-sinh-tuổi--quyền-riêng-tư)
6. [Tích hợp Hệ thống Thông báo & Email Tri Ân (Bevel Design)](#6-tích-hợp-hệ-thống-thông-báo--email-tri-ân-bevel-design)
7. [Ma trận Bất biến An toàn & Chống Gian lận (Security Invariants)](#7-ma-trận-bất-biến-an-toàn--chống-gian-lận-security-invariants)
8. [Bằng chứng Thực nghiệm & Kết quả Kiểm thử Hệ thống (Verification Proof)](#8-bằng-chứng-thực-nghiệm--kết-quả-kiểm-thử-hệ-thống-verification-proof)

---

## 1. 🎯 Tóm tắt Điều hành (Executive Summary)

Phân hệ Ngày sinh Người dùng đã được tái cấu trúc và nâng cấp toàn diện từ một thuộc tính thụ động trong hồ sơ thành một **Động cơ Tri ân & Trải nghiệm Người dùng (User Celebration Engine)** hoàn chỉnh:
- **Chuẩn hóa dữ liệu:** Loại bỏ hoàn toàn `java.util.Date`, thay thế bằng `java.time.LocalDate` với định dạng ISO `yyyy-MM-dd`.
- **Đồng bộ CSDL Oracle:** Triển khai script Flyway `V19__align_user_birthday_schema.sql` chuyển đổi kiểu dữ liệu cột `DATE_OF_BIRTH` sang `DATE` và bổ sung các trường vòng đời `DOB_UPDATED_AT`, `HIDE_BIRTH_YEAR`.
- **Cơ chế Chống Gian Lận (Anti-Fraud Guard):** Chặn hành vi liên tục đổi ngày sinh để nhận voucher qua cơ chế **365-Day Cooldown**.
- **Tính toán Ngữ cảnh UI (Frontend Projections):** Tự động trả về các cờ `isBirthdayToday`, `isBirthdayMonth` và số ngày đếm ngược `daysUntilBirthday`.
- **Tích hợp Notification:** Bổ sung template email chúc mừng sinh nhật chuẩn **Shadcn Optical Bevel** kèm khung Voucher quà tặng tương tác.

---

## 2. 🔍 Chẩn đoán Vấn đề & Nguyên nhân Gốc rễ (Root Cause Analysis)

```
+----------------------------------------------------------------------------------------------------+
|                                    MA TRẬN NGUYÊN NHÂN & KHẮC PHỤC                                 |
+----------------------------------------------------------------------------------------------------+
| 1. Timezone Drift       : java.util.Date mang timestamp -> Lệch ngày UTC+7 -> Thay bằng LocalDate |
| 2. Schema Validation    : Hibernate 6 Types#DATE vs Oracle TIMESTAMP -> Flyway V19 Cast & Alter   |
| 3. Birthday Hopping     : Đổi ngày sinh hàng ngày trục lợi -> Giới hạn 365 ngày Cooldown          |
| 4. Missing UI Analytics : FE không biết user có sinh nhật hôm nay -> Backend tính sẵn 3 cờ        |
+----------------------------------------------------------------------------------------------------+
```

### 2.1. Lỗi Lệch Múi Giờ và Nợ Kỹ Thuật `java.util.Date`
- **Hiện tượng:** Người dùng ở Việt Nam (UTC+7) chọn ngày sinh `15/05/1998`, nhưng khi lưu vào CSDL hoặc serialize qua Jackson thì bị lùi về `14/05/1998 17:00:00 UTC`.
- **Nguyên nhân gốc rễ:** `java.util.Date` lưu trữ cả giờ, phút, giây và phụ thuộc vào Timezone của máy chủ/client.
- **Giải pháp:** Sử dụng `java.time.LocalDate` (chỉ lưu ngày/tháng/năm độc lập với múi giờ).

### 2.2. Lỗi Xung đột Schema Validation Hibernate 6 với Oracle DB
- **Hiện tượng:** Khi chạy ứng dụng với profile `dev` (`spring.jpa.hibernate.ddl-auto: validate`), Spring Boot gặp lỗi:
  ```
  Schema-validation: wrong column type encountered in column [date_of_birth] in table [users];
  found [timestamp (Types#TIMESTAMP)], but expecting [date (Types#DATE)]
  ```
- **Nguyên nhân gốc rễ:** Cột `date_of_birth` trong Oracle DB trước đây được tạo kiểu `TIMESTAMP` (do mapping cũ của `java.util.Date`). Hibernate 6 yêu cầu `LocalDate` phải khớp với kiểu `Types#DATE` (tương ứng với cột `DATE` trong Oracle).
- **Giải pháp:** Viết Flyway migration script V19 để cast dữ liệu và alter kiểu cột thành `DATE`.

### 2.3. Lỗ hổng Trục lợi Ngày Sinh (Birthday Hopping / Fraud)
- **Hiện tượng:** Người dùng có thể gọi `PUT /api/auth/me` để đổi ngày sinh sang ngày hiện tại mỗi ngày để liên tục nhận các ưu đãi sinh nhật.
- **Giải pháp:** Kiểm tra `dobUpdatedAt`. Nếu người dùng đã từng đổi ngày sinh trong vòng 365 ngày gần nhất, hệ thống từ chối và yêu cầu liên hệ CSKH.

---

## 3. 🏗️ Kiến trúc & Cơ chế Vận hành Toàn diện

```
                                  ┌──────────────────────────────────────────┐
                                  │       CLIENT (Web App / Mobile App)      │
                                  └────────────────────┬─────────────────────┘
                                                       │
                           ┌───────────────────────────┴───────────────────────────┐
                           │                                                       │
             GET /api/auth/me                                            PUT /api/auth/me
             (Lấy hồ sơ & cờ sinh nhật)                                  (Cập nhật ngày sinh)
                           │                                                       │
                           ▼                                                       ▼
+--------------------------------------------------------------------------------------------------+
|                                        USER SERVICE (IAM)                                        |
|  1. Projection Engine:                                                                           |
|     - isBirthdayToday = (dob.month == now.month && dob.day == now.day)                           |
|     - isBirthdayMonth = (dob.month == now.month)                                                 |
|     - daysUntilBirthday = ChronoUnit.DAYS.between(now, nextBirthday)                             |
|  2. Anti-Fraud & Validation:                                                                     |
|     - @Past & Age Validation: 10 <= Age <= 120                                                    |
|     - Cooldown Guard: dobUpdatedAt > now - 365 days -> throw INVALID_CREDENTIALS                 |
+------------------------------------+-------------------------------------------------------------+
                                     │
                                     v
+-------------------------------------------------------------+ +----------------------------------+
|                    ORACLE DATABASE (USERS)                  | |        NOTIFICATION MODULE       |
| - DATE_OF_BIRTH: DATE (Chuyển từ TIMESTAMP qua V19)         | | - TemplateCode.BIRTHDAY_GREETING |
| - DOB_UPDATED_AT: TIMESTAMP (Theo dõi Cooldown 365 ngày)    | | - Template: birthday-email.html  |
| - HIDE_BIRTH_YEAR: NUMBER(1) (Tùy chọn ẩn năm sinh)         | | - Producer: sendBirthdayGreeting |
+-------------------------------------------------------------+ +----------------------------------+
```

---

## 4. 🗄️ Đặc tả CSDL & Flyway Migration V19

Tệp migration: [`src/main/resources/db/migration/V19__align_user_birthday_schema.sql`](src/main/resources/db/migration/V19__align_user_birthday_schema.sql)

```sql
-- V19: Align User Birthday Schema
-- Convert USERS.DATE_OF_BIRTH from TIMESTAMP to DATE for LocalDate mapping
-- Add DOB_UPDATED_AT (TIMESTAMP) and HIDE_BIRTH_YEAR (NUMBER(1)) columns

-- Step 1: Add temporary DATE column for date_of_birth
ALTER TABLE USERS ADD (DATE_OF_BIRTH_TMP DATE);

-- Step 2: Copy existing TIMESTAMP data as DATE
UPDATE USERS SET DATE_OF_BIRTH_TMP = CAST(DATE_OF_BIRTH AS DATE) WHERE DATE_OF_BIRTH IS NOT NULL;
COMMIT;

-- Step 3: Drop the original TIMESTAMP column
ALTER TABLE USERS DROP COLUMN DATE_OF_BIRTH;

-- Step 4: Rename temp column to original name
ALTER TABLE USERS RENAME COLUMN DATE_OF_BIRTH_TMP TO DATE_OF_BIRTH;

-- Step 5: Add birthday lifecycle and privacy columns
ALTER TABLE USERS ADD (
    DOB_UPDATED_AT TIMESTAMP,
    HIDE_BIRTH_YEAR NUMBER(1) DEFAULT 0
);

-- Step 6: Populate default values for existing records
UPDATE USERS SET HIDE_BIRTH_YEAR = 0 WHERE HIDE_BIRTH_YEAR IS NULL;
COMMIT;
```

---

## 5. 🛠️ Đặc tả Chi tiết API Endpoints & Request/Response Contracts

### 5.1. `GET /api/auth/me` — Lấy Profile & Các Cờ Trạng thái Sinh nhật

Lấy thông tin tài khoản người dùng hiện tại kèm các phân tích ngữ cảnh sinh nhật tự động.

- **Method:** `GET`
- **Path:** `/api/auth/me`
- **Authentication:** `Bearer JWT` (Bắt buộc)

#### Response Example (`200 OK`):
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
    "dateOfBirth": "1998-09-26",
    "hideBirthYear": false,
    "isBirthdayToday": true,
    "isBirthdayMonth": true,
    "daysUntilBirthday": 0,
    "avatarUrl": "https://s3.ddicg.com/avatars/user-100.png",
    "gender": "MALE",
    "rank": "PLATINUM",
    "status": "ACTIVE",
    "roles": ["ROLE_USER"]
  }
}
```

#### Hướng dẫn Frontend Tương tác:
- Nếu `isBirthdayToday == true`: Kích hoạt hiệu ứng pháo hoa (Confetti Canvas), đổi khung avatar sang màu vàng hoàng kim và hiển thị popup mừng sinh nhật.
- Nếu `isBirthdayMonth == true`: Hiển thị banner ưu đãi: *"Chào mừng tháng sinh nhật của bạn! Đừng quên sử dụng Voucher đặc quyền."*
- `daysUntilBirthday`: Hiển thị huy hiệu đếm ngược: *"Còn **X** ngày nữa là đến sinh nhật bạn!"*.

---

### 5.2. `PUT /api/auth/me` — Cập nhật Ngày sinh, Tuổi & Quyền Riêng tư

Cập nhật thông tin cá nhân của người dùng đang đăng nhập.

- **Method:** `PUT`
- **Path:** `/api/auth/me`
- **Authentication:** `Bearer JWT`
- **Content-Type:** `application/json`

#### Request Body Schema (`UpdateProfileRequest`):
| Thuộc tính | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `fullName` | String | Tùy chọn | Họ và tên đầy đủ |
| `phoneNumber` | String | Tùy chọn | Số điện thoại liên hệ |
| `dateOfBirth` | LocalDate (`yyyy-MM-dd`) | `@Past`, Độ tuổi từ 10 đến 120 | Ngày sinh hợp lệ |
| `hideBirthYear` | Boolean | Tùy chọn | Tùy chọn ẩn năm sinh trên hồ sơ |
| `gender` | String | `MALE`, `FEMALE`, `OTHER` | Giới tính |
| `avatarUrl` | String | Tùy chọn | Đường dẫn ảnh đại diện |

#### Request Example:
```json
{
  "fullName": "Alex Developer",
  "phoneNumber": "0901234567",
  "dateOfBirth": "1998-09-26",
  "hideBirthYear": true,
  "gender": "MALE"
}
```

#### Response Thành công (`200 OK`):
Trả về đối tượng `MyProfileResponse` đầy đủ với các giá trị đã cập nhật và cờ sinh nhật được tính toán lại ngay lập tức.

#### Xử lý Lỗi Nghiệp vụ:
- **`400 BAD_REQUEST` (Tuổi không hợp lệ / Ngày tương lai):**
  ```json
  {
    "status": {
      "code": 400,
      "message": "Ngày sinh không hợp lệ hoặc độ tuổi phải từ 10 đến 120 tuổi."
    },
    "data": null
  }
  ```
- **`401 UNAUTHORIZED / INVALID_CREDENTIALS` (Vi phạm Cooldown 365 ngày):**
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

## 6. 💌 Tích hợp Hệ thống Thông báo & Email Tri Ân (Bevel Design)

1. **Enum Template:** `TemplateCode.BIRTHDAY_GREETING("mail/birthday-email", "Chúc mừng sinh nhật quý khách!")`.
2. **Template HTML:** [`src/main/java/com/ddicg/erp/modules/notification/resources/templates/mail/birthday-email.html`](src/main/java/com/ddicg/erp/modules/notification/resources/templates/mail/birthday-email.html)
   - Thiết kế chuẩn **Shadcn Optical Bevel** với gam màu Vàng/Hổ phách sang trọng (`#F59E0B` -> `#D97706`).
   - Khung Voucher quà tặng sinh nhật hiển thị phần trăm giảm giá theo Hạng thành viên.
   - Hộp sao chép mã Voucher (Copy-to-Clipboard) với animation tích xanh mượt mà.
   - Nút bấm Bevel 3D có hiệu ứng ánh kim (Specular rim) và đổ bóng thực tế.
3. **Kafka Event Producer (`NotificationEventProducer`):**
   ```java
   public void sendBirthdayGreetingEmail(String recipient, String username, String voucherCode, Integer discountPercent, String giftUrl) {
       // Tạo payload kèm Deduplication Key: BIRTHDAY:{recipient}:{year}
       // Dispatch vào Kafka queue để xử lý bất đồng bộ
   }
   ```

---

## 7. 🔐 Ma trận Bất biến An toàn & Chống Gian lận (Security Invariants)

| Biện pháp | Cơ chế Thực thi | Nguy cơ Ngăn chặn |
| :--- | :--- | :--- |
| **Strict Date-only Storage** | `java.time.LocalDate` mapped to Oracle `DATE` | Loại bỏ 100% rủi ro lệch ngày do múi giờ server/client. |
| **Past Date & Age Guard** | `@Past` + Kiểm tra logic `10 <= Age <= 120` | Ngăn chặn nhập ngày sinh tương lai hoặc năm sinh phi lý. |
| **365-Day Cooldown** | Ghi nhận `dob_updated_at` trên CSDL | Chống hành vi đổi ngày sinh liên tục để trục lợi mã giảm giá và quà tặng. |
| **Email Deduplication** | Kafka Message Key `BIRTHDAY:{email}:{year}` | Đảm bảo mỗi tài khoản chỉ nhận tối đa 1 email tri ân sinh nhật mỗi năm. |
| **Privacy Protection** | Cờ `hide_birth_year` | Bảo vệ thông tin định danh cá nhân (PII) khi hiển thị hồ sơ công khai. |

---

## 8. 🧪 Bằng chứng Thực nghiệm & Kết quả Kiểm thử Hệ thống (Verification Proof)

### 8.1. Kiểm thử Thực tế Migration & Khởi động Server (Oracle 21c Live Execution)
```
16:44:36.459 [restartedMain] INFO  o.f.core.internal.command.DbMigrate - Current version of schema "SPRING_APP": 18
16:44:36.471 [restartedMain] INFO  o.f.core.internal.command.DbMigrate - Migrating schema "SPRING_APP" to version "19 - align user birthday schema"
16:44:36.708 [restartedMain] INFO  o.f.core.internal.command.DbMigrate - Successfully applied 1 migration to schema "SPRING_APP", now at version v19 (execution time 00:00.225s)
16:44:38.022 [restartedMain] INFO  o.s.o.j.LocalContainerEntityManagerFactoryBean - Initialized JPA EntityManagerFactory for persistence unit 'default'
16:44:42.385 [restartedMain] INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port 8089 (http) with context path '/'
16:44:42.489 [restartedMain] INFO  com.ddicg.erp.ErpApplication - Started ErpApplication in 8.72 seconds (process running for 8.967)
```

### 8.2. Kết quả Unit & Integration Test Suites (`UserServiceBirthdayTest`)
```
[INFO] Running com.ddicg.erp.modules.iam.service.UserServiceBirthdayTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.722 s - in UserServiceBirthdayTest
[INFO] 
[INFO] Results:
[INFO] Tests run: 274, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```
- `getMyProfile_WhenBirthdayIsToday_ShouldReturnBirthdayFlagsTrue`: ✅ **Passed**
- `getMyProfile_WhenBirthdayIsThisMonth_ShouldReturnIsBirthdayMonthTrueAndTodayFalse`: ✅ **Passed**
- `updateMyProfile_WhenFirstTimeUpdatingDob_ShouldSucceed`: ✅ **Passed**
- `updateMyProfile_WhenAgeLessThan10_ShouldThrowValidationFailed`: ✅ **Passed**
- `updateMyProfile_WhenAgeGreaterThan120_ShouldThrowValidationFailed`: ✅ **Passed**
- `updateMyProfile_WhenUpdatingWithinCooldown_ShouldThrowInvalidCredentials`: ✅ **Passed**
- `updateMyProfile_WhenUpdatingAfterCooldown_ShouldSucceed`: ✅ **Passed**
