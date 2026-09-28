# ADR-001: User Birthday Data Modeling, Schema Alignment, and Anti-Fraud Lifecycle

## Status
Accepted

## Date
2026-09-26

## Context
Trong hệ thống ERP Spring Boot, ngày sinh (`dateOfBirth`) ban đầu được lưu trữ như một thuộc tính cơ bản với kiểu dữ liệu `java.util.Date`. Khi hệ thống mở rộng sang phân hệ Notification (gửi email tri ân, voucher sinh nhật) và tích hợp đa nền tảng (Web/Mobile Frontend ở múi giờ UTC+7), các vấn đề kỹ thuật và lỗ hổng bảo mật sau đã xuất hiện:

1. **Lệch múi giờ (Timezone Drift):** `java.util.Date` mang theo thành phần thời gian và phụ thuộc múi giờ. Khi client gửi ngày sinh `15/05/1998` ở UTC+7, quá trình serialization/deserialization có thể dịch chuyển mốc thời gian thành `14/05/1998 17:00:00 UTC`, làm sai lệch ngày sinh của khách hàng.
2. **Xung đột Hibernate 6 Schema Validation với Oracle Database:** Cột `date_of_birth` trong Oracle DB trước đó là kiểu `TIMESTAMP`. Khi ánh xạ sang `java.time.LocalDate`, Hibernate 6 yêu cầu kiểu cột JDBC là `Types#DATE` (`DATE` trong Oracle). Khi chạy với cấu hình `spring.jpa.hibernate.ddl-auto: validate`, ứng dụng gặp lỗi `SchemaManagementException` và không thể khởi động context.
3. **Lỗ hổng trục lợi ưu đãi sinh nhật (Birthday Hopping / Fraud):** Endpoint cập nhật hồ sơ (`PUT /api/auth/me`) không có ràng buộc tần suất sửa ngày sinh. Kẻ gian có thể liên tục đổi ngày sinh thành ngày hôm nay để nhận voucher/quà tặng vô hạn.
4. **Thiếu kiểm soát độ tuổi và định dạng:** Cho phép gửi ngày trong tương lai hoặc độ tuổi phi lý nếu không có validation.

## Decision

1. **Chuẩn hóa Kiểu Dữ Liệu Thời Gian:**
   - Chuyển toàn bộ kiểu dữ liệu `dateOfBirth` từ `java.util.Date` sang **`java.time.LocalDate`** trên `User.java`, `UserDto.java`, `UpdateProfileRequest.java` và `MyProfileResponse.java`.
   - Bổ sung chú thích `@Past` và `@JsonFormat(pattern = "yyyy-MM-dd")`.

2. **Thực hiện Flyway Migration V19 trên CSDL Oracle:**
   - Tạo file migration `V19__align_user_birthday_schema.sql` để chuyển đổi kiểu cột `USERS.DATE_OF_BIRTH` từ `TIMESTAMP` sang `DATE`.
   - Bổ sung 2 cột: `DOB_UPDATED_AT (TIMESTAMP)` (ghi nhận thời điểm sửa ngày sinh) và `HIDE_BIRTH_YEAR (NUMBER(1) DEFAULT 0)` (tùy chọn quyền riêng tư).

3. **Thiết lập Bất biến Chống Gian Lận (Anti-Fraud Invariant):**
   - Áp dụng quy tắc **365-Day Cooldown**: Người dùng chỉ được phép cập nhật ngày sinh tối đa **1 lần mỗi năm**. Nếu cố tình đổi lại trong vòng 365 ngày, hệ thống ném `BusinessException(ErrorCode.INVALID_CREDENTIALS)`.
   - Kiểm tra giới hạn độ tuổi hợp lý: Độ tuổi bắt buộc nằm trong khoảng **10 đến 120 tuổi**.

4. **Tích hợp Phân hệ Thông báo & Email Tri Ân:**
   - Thêm mã `TemplateCode.BIRTHDAY_GREETING` và tạo mẫu email `birthday-email.html` chuẩn thiết kế **Shadcn Optical Bevel** với nút sao chép voucher tương tác.
   - Thêm tiện ích gửi email qua Kafka `NotificationEventProducer.sendBirthdayGreetingEmail` với Deduplication Key `BIRTHDAY:{recipient}:{year}`.

## Alternatives Considered

### Giữ nguyên `java.util.Date` và xử lý múi giờ ở tầng Controller/Mapper
- *Ưu điểm:* Không cần thay đổi kiểu dữ liệu Entity và không phát sinh xung đột validation cột CSDL.
- *Nhược điểm:* Nợ kỹ thuật kéo dài; dễ phát sinh lỗi tiềm ẩn khi dữ liệu đi qua nhiều tầng service, DTO, Kafka stream. `java.util.Date` đã lỗi thời từ Java 8.
- *Lý do bác bỏ:* Cần giải quyết dứt điểm vấn đề lệch ngày ở mức độ Type System.

### Sử dụng Redis Cooldown thay vì lưu cột `dob_updated_at` trong CSDL
- *Ưu điểm:* Không cần migration CSDL.
- *Nhược điểm:* Redis là bộ nhớ đệm (In-Memory). Nếu Redis bị restart hoặc flush, khóa Cooldown 365 ngày có nguy cơ bị mất, tạo kẽ hở cho kẻ gian bypass giới hạn.
- *Lý do bác bỏ:* Dữ liệu bảo mật liên quan đến tần suất nhận quyền lợi hàng năm cần được lưu trữ vĩnh viễn (Durable Persistence) trong CSDL quan hệ Oracle.

## Consequences

- **Tích cực:**
  - 100% không còn hiện tượng lệch ngày sinh do múi giờ.
  - Spring Boot khởi động mượt mà với `spring.jpa.hibernate.ddl-auto: validate` trên Oracle 21c.
  - Loại bỏ hoàn toàn rủi ro trục lợi quà tặng sinh nhật lặp lại nhiều lần trong năm.
  - Dữ liệu API sạch sẽ, rõ ràng, tuân thủ nghiêm ngặt nguyên lý Single Responsibility.
- **Yêu cầu triển khai:**
  - Cần chạy Flyway Migration V19 khi deploy lên các môi trường Staging/Production trước khi start ứng dụng.
