# ADR-003: Event-Driven Messaging với Kafka KRaft và SASL_PLAINTEXT

## Trạng thái (Status)
**Accepted**

## Ngày (Date)
2026-08-16

## Bối cảnh (Context)
Khi các hành động quan trọng diễn ra (ví dụ: tạo đơn hàng `OrderCreated`, thanh toán thành công `PaymentCompleted`, thay đổi trạng thái tồn kho), hệ thống cần:
- Kích hoạt các tác vụ thứ cấp (đồng bộ sổ cái kế toán Fineract, gửi email/thông báo) mà không làm chậm luồng xử lý chính của người dùng.
- Đảm bảo tính bảo mật và xác thực khi giao tiếp qua message broker.

## Quyết định (Decision)
Triển khai **Apache Kafka 3.8 (KRaft Mode)** với cơ chế bảo mật **`SASL_PLAINTEXT`**:
- Bỏ qua ZooKeeper, sử dụng cơ chế đồng thuận nội bộ KRaft (Kafka Raft Metadata Mode) giúp cụm Kafka nhẹ và khởi động nhanh hơn.
- Cấu hình SASL Authentication (PLAIN mechanism) với thông tin chứng thực an toàn qua biến môi trường.
- Triển khai `Spring Kafka` (`KafkaTemplate`, `@KafkaListener`) để publish và consume domain events.

## Các phương án cân nhắc (Alternatives Considered)

### 1. RabbitMQ
- **Ưu điểm**: Nhẹ, hỗ trợ routing linh hoạt (AMQP).
- **Lý do chọn Kafka**: Kafka có khả năng lưu trữ nhật ký sự kiện (event log persistence / replayability), phù hợp hơn cho các hệ thống tài chính ERP cần audit log và event sourcing.

### 2. Spring In-Memory Events (`ApplicationEventPublisher`)
- **Ưu điểm**: Đơn giản, không cần cài thêm broker.
- **Nhược điểm**: Mất sự kiện khi ứng dụng restart, không thể mở rộng ra các consumer tiến trình ngoài hoặc worker độc lập.

## Hệ quả (Consequences)
- **Tích cực**:
  - Tách rời hoàn toàn (decoupling) giữa module Bán hàng (`order`) và Sổ cái (`fineract`).
  - Hệ thống chịu tải tốt hơn nhờ xử lý bất đồng bộ các tác vụ nặng.
  - Bảo mật kết nối nội bộ thông qua SASL Authentication.
- **Lưu ý**:
  - Cần cấu hình retry và Dead Letter Topic (DLT) cho các message tiêu thụ thất bại.
