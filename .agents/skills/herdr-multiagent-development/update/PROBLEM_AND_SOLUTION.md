# Báo cáo Vấn đề và Giải pháp Tối ưu: Database Flyway trong Herdr Multi-Agent

## 1. Vấn đề hiện tại trong Skill `herdr-multiagent-development`
Trong phiên bản hiện tại của quy trình Herdr Parallel Edition, `ARTICLE 3` (trong `SKILL.md`) định nghĩa việc chia Wave bắt đầu ngay lập tức vào **Wave 1: Parallel Wave Name - Independent Tasks** phân tán hoàn toàn đồng thời.

### Các rủi ro chí mạng khi áp dụng cho Backend (ví dụ Spring Boot + Flyway):
- **Xung đột Global State (Trạng thái toàn cục):** Các thao tác liên quan đến cơ sở dữ liệu (tạo bảng, sửa schema) thông qua file migration (như Flyway `.sql`) đòi hỏi tính tuần tự tuyệt đối (ví dụ `V1__...`, `V2__...`). 
- **Merge Conflicts:** Nếu `worker-1` và `worker-2` trong Wave 1 cùng cố gắng tạo file `V1__Init_Tables.sql` trong cùng một thư mục `db/migration`, hệ thống khi gộp mã nguồn (merge) sẽ bị conflict. Nghiêm trọng hơn, khi service khởi động sẽ báo lỗi Flyway checksum hoặc validation failed.
- **Rác cấu hình chung (Cross-cutting Concerns):** Các Worker cũng dễ dàng dẫm chân lên nhau nếu cùng sửa file `pom.xml`, `build.gradle`, hoặc `SecurityConfig.java`.

### Các file bị ảnh hưởng trong Skill:
1. `.agents/skills/herdr-multiagent-development/SKILL.md` (Đoạn `ARTICLE 3`)
2. `.agents/skills/herdr-multiagent-development/supervisor-prompt.md` (Hướng dẫn lập plan cho Supervisor)

---

## 2. Giải pháp cập nhật (Cơ chế Wave 0)
Để giải quyết bài toán trên, kỹ thuật **"Centralized DBA & Global Configs"** sẽ được nhúng cứng vào tiêu chuẩn chia Wave của Herdr:

- **Thêm "Wave 0: Global State & Foundation":** Bất kỳ thay đổi nào liên quan đến Database Migration (Flyway/Liquibase) hoặc cấu hình dự án toàn cục (Project config, Dependency) BẮT BUỘC phải được cô lập vào một Wave tuần tự đầu tiên (Wave 0). 
- **Quy tắc Single Pair trong Wave 0:** Wave 0 chỉ cho phép duy nhất một cặp (`worker-dba` / `censor-dba`) chạy để định nghĩa toàn bộ Database Schema, Migration Files.
- **Push các task nghiệp vụ xuống Wave 1+:** Chỉ khi Wave 0 xong (schema đã chốt), Wave 1 mới được mở ra để các Worker implement độc lập các Entities, Repositories song song mà không sợ tranh chấp version Flyway.

---

## 3. Chi tiết thay đổi (Diff)
1. **Trong `SKILL.md`:** 
   - Thêm khoản mục 3.2 mới: Quy tắc định nghĩa DB Migration.
   - Sửa cấu trúc Plan mẫu: Thêm đoạn `Wave 0: Global State (Sequential)`.
2. **Trong `supervisor-prompt.md`:**
   - Thêm chỉ thị bắt buộc Supervisor phải tạo Wave 0 nếu tính năng yêu cầu sửa Database hoặc Dependency.