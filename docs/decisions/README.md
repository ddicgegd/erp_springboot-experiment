# Architecture Decision Records (ADRs)

Tài liệu ghi nhận các quyết định kiến trúc then chốt, bối cảnh lựa chọn, các phương án cân nhắc và hệ quả kỹ thuật của hệ thống **ERP Spring Boot**.

## Danh mục quyết định

| Mã ADR | Tiêu đề | Trạng thái | Ngày | Tóm tắt |
| :--- | :--- | :--- | :--- | :--- |
| [ADR-001](./ADR-001-modular-monolith-architecture.md) | Kiến trúc Modular Monolith | **Accepted** | 2026-08-16 | Chọn mô hình Modular Monolith thay vì Microservices để bảo toàn tính toàn vẹn giao dịch và đơn giản hóa vận hành |
| [ADR-002](./ADR-002-primary-database-oracle-jpa.md) | Cơ sở dữ liệu chính Oracle XE & JPA | **Accepted** | 2026-08-16 | Sử dụng Oracle Database XE 21c cùng Spring Data JPA / Hibernate cho dữ liệu giao dịch tài chính ACID |
| [ADR-003](./ADR-003-event-driven-messaging-kafka-kraft.md) | Event-Driven Messaging với Kafka KRaft SASL | **Accepted** | 2026-08-16 | Triển khai Apache Kafka KRaft mode kết hợp bảo mật SASL_PLAINTEXT cho Domain Events giữa các module |
| [ADR-004](./ADR-004-multi-tier-caching-and-object-storage.md) | Chiến lược Lưu trữ Đa tầng (Redis + MinIO) | **Accepted** | 2026-08-16 | Phân tầng lưu trữ: Redis cho session/cart và MinIO cho hóa đơn/media assets S3-compatible |
| [ADR-005](./ADR-005-apache-fineract-ledger-integration.md) | Tích hợp Sổ cái & Core Banking với Apache Fineract | **Accepted** | 2026-08-16 | Tích hợp bất đồng bộ qua Kafka Events để đồng bộ bút toán kế toán sang Apache Fineract |

## Chu trình vòng đời của ADR
```
PROPOSED ──► ACCEPTED ──► (SUPERSEDED hoặc DEPRECATED)
```
* **Không xóa các ADR cũ**: Giữ lại để bảo toàn bối cảnh lịch sử.
* Khi thay đổi thiết kế: Tạo một ADR mới ghi rõ `Supersedes ADR-xxx`.
