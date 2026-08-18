# ADR-001: Lựa chọn kiến trúc Modular Monolith

## Trạng thái (Status)
**Accepted**

## Ngày (Date)
2026-08-16

## Bối cảnh (Context)
Hệ thống ERP quản lý luồng nghiệp vụ chặt chẽ giữa Bán hàng (Orders), Kho hàng & Sản phẩm (Merchandise), Giỏ hàng (Cart), Định danh (IAM) và Hạch toán sổ cái (Fineract).
Yêu cầu cốt lõi:
- Đảm bảo tính nhất quán dữ liệu giao dịch cao (ACID) khi tạo và xử lý đơn hàng.
- Tránh độ phức tạp phân tán (distributed tracing, 2-phase commit, network latency) trong giai đoạn xây dựng và kiểm thử hệ thống.
- Đội ngũ phát triển cần quy trình deploy tinh gọn (single deployment unit).

## Quyết định (Decision)
Triển khai hệ thống theo mô hình **Modular Monolith** trên nền tảng **Spring Boot 3.x / Java 21**:
- Mã nguồn được phân tách theo ranh giới module rõ ràng (`com.ddicg.erp.modules.{iam, merchandise, cart, order, fineract}`).
- Giao tiếp giữa các module đồng bộ qua Service Interfaces được định nghĩa rõ ràng, hoặc bất đồng bộ qua Domain Events / Kafka.
- Tách biệt tầng `core` (security, config, exception, common) và tầng nghiệp vụ `modules`.

## Các phương án cân nhắc (Alternatives Considered)

### 1. Microservices Architecture
- **Ưu điểm**: Khả năng scale độc lập từng service, công nghệ linh hoạt.
- **Nhược điểm**: Chi phí hạ tầng cao, phức tạp khi đảm bảo tính toàn vẹn dữ liệu xuyên suốt (Saga Pattern), độ trễ mạng lớn giữa các module gọi nhau thường xuyên.
- **Lý do từ chối**: Chưa cần thiết ở quy mô hiện tại và gây chậm trễ tốc độ phát triển.

### 2. Traditional Layered Monolith (Chỉ chia theo Controller / Service / Repo)
- **Ưu điểm**: Dễ bắt đầu.
- **Nhược điểm**: Mã nguồn nhanh chóng bị đan xen (spaghetti code), khó refactor và gần như không thể tách rời module sau này.
- **Lý do từ chối**: Không tạo được ranh giới rõ ràng giữa các miền nghiệp vụ (Domain Boundaries).

## Hệ quả (Consequences)
- **Tích cực**:
  - Dễ triển khai, kiểm thử và debug cục bộ (chỉ cần 1 tiến trình Spring Boot).
  - Tận dụng được transaction nội bộ của Spring Boot (`@Transactional`) trên Oracle DB.
  - Sẵn sàng lộ trình chuyển đổi sang Microservices độc lập trong tương lai mà không phải đập đi viết lại.
- **Rủi ro & Giảm thiểu**:
  - Nguy cơ phụ thuộc vòng giữa các module $\to$ Kiểm soát nghiêm ngặt qua quy ước không cho phép module này truy cập trực tiếp `Repository` của module khác.
