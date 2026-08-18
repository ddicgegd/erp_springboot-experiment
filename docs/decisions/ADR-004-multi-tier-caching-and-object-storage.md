# ADR-004: Chiến lược Lưu trữ Đa tầng (Redis + MinIO)

## Trạng thái (Status)
**Accepted**

## Ngày (Date)
2026-08-16

## Bối cảnh (Context)
Một hệ thống ERP có các loại dữ liệu với đặc tính truy cập và lưu trữ hoàn toàn khác biệt:
1. **Dữ liệu tạm thời / Tốc độ cao**: Giỏ hàng người dùng (Cart), JWT token blacklist, caching dữ liệu sản phẩm.
2. **Dữ liệu nhị phân / Tập tin lớn (Binary & Files)**: Ảnh sản phẩm, hóa đơn PDF, chứng từ kế toán.
3. **Dữ liệu quan hệ**: Bảng đơn hàng, người dùng, kho hàng (đã lưu trên Oracle DB).

## Quyết định (Decision)
Triển khai giải pháp lưu trữ chuyên biệt theo từng tầng dữ liệu:
- **Tầng Caching & In-Memory (Redis Alpine)**:
  - Lưu giỏ hàng tạm thời của khách hàng với TTL (Time-To-Live).
  - Caching danh mục sản phẩm (Product Catalog) để giảm tải I/O trên Oracle DB.
- **Tầng Lưu trữ Đối tượng (MinIO - S3 Compatible)**:
  - Lưu trữ toàn bộ file tải lên (ảnh, PDF hóa đơn, chứng từ) trong MinIO bucket.
  - Ứng dụng chỉ lưu đường dẫn URL / Object Key trong Oracle DB.

## Các phương án cân nhắc (Alternatives Considered)

### 1. Lưu file trực tiếp vào Oracle Database (BLOB / CLOB)
- **Nhược điểm**: Làm phình to dung lượng database, backup chậm chạp, tốn I/O của DB chính.
- **Lý do từ chối**: Phản mẫu (anti-pattern) trong thiết kế hệ thống hiện đại.

### 2. Lưu file vào Local File System của Server
- **Nhược điểm**: Khó scale ngang (khi chạy nhiều container/server), mất file khi container bị hủy.
- **Lý do từ chối**: Không cloud-native.

## Hệ quả (Consequences)
- **Tích cực**:
  - Oracle DB luôn giữ kích thước tinh gọn, tối ưu tốc độ truy vấn nghiệp vụ.
  - Phản hồi thao tác giỏ hàng cực nhanh qua Redis.
  - MinIO cung cấp chuẩn API tương thích 100% AWS S3, dễ dàng chuyển đổi lên AWS S3 / Cloudflare R2 khi deploy production.
