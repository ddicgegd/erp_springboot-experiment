# ADR-002: Cơ sở dữ liệu chính Oracle XE & Spring Data JPA

## Trạng thái (Status)
**Accepted**

## Ngày (Date)
2026-08-16

## Bối cảnh (Context)
Hệ thống ERP đòi hỏi lưu trữ dữ liệu có cấu trúc, quan hệ chặt chẽ (Users, Products, Categories, Attributes, Orders, Order Items, Transactions).
Yêu cầu:
- Hỗ trợ đầy đủ các tính chất ACID cho các giao dịch đơn hàng và thanh toán.
- Khả năng quản lý quan hệ phức tạp và tính toàn vẹn khóa ngoại (Foreign Keys).
- Tích hợp chuẩn mực với hệ sinh thái Spring Boot.

## Quyết định (Decision)
Sử dụng **Oracle Database 21c (gvenzl/oracle-xe:21-slim-faststart)** kết hợp **Spring Data JPA / Hibernate**:
- Kết nối thông qua Oracle JDBC Driver (`com.oracle.database.jdbc:ojdbc11`).
- Quản lý entity mapping bằng annotations JPA tiêu chuẩn (`@Entity`, `@Table`, `@Id`, `@GeneratedValue`).
- Sử dụng Hibernate DDL update / Flyway migrations để đồng bộ cấu trúc bảng.

## Các phương án cân nhắc (Alternatives Considered)

### 1. PostgreSQL / MySQL
- **Ưu điểm**: Phổ biến, chi phí thấp, hỗ trợ JSON mạnh.
- **Lý do lựa chọn Oracle**: Oracle Database là tiêu chuẩn công nghiệp trong các hệ thống ERP doanh nghiệp và tương thích sâu với các tiêu chuẩn ngân hàng / tài chính của dự án.

### 2. MongoDB (NoSQL)
- **Ưu điểm**: Schema linh hoạt, ghi nhanh.
- **Lý do từ chối**: Dữ liệu ERP có tính quan hệ cao (bán hàng - kho - kế toán); NoSQL gây khó khăn cho việc đảm bảo tính toàn vẹn tài chính và báo cáo đối soát.

## Hệ quả (Consequences)
- **Tích cực**:
  - Đảm bảo tính toàn vẹn dữ liệu nghiêm ngặt cấp doanh nghiệp.
  - Tối ưu hóa truy vấn qua Spring Data JPA Repositories và Hibernate Caching.
- **Lưu ý**:
  - Cần chú ý cú pháp Oracle SQL khi viết Native Query (phân trang `OFFSET/FETCH FIRST`, sequence generation thay cho auto-increment thông thường).
