# ADR-005: Tích hợp Sổ cái & Core Banking với Apache Fineract

## Trạng thái (Status)
**Accepted**

## Ngày (Date)
2026-08-16

## Bối cảnh (Context)
Hệ thống ERP cần chức năng hạch toán kế toán, quản lý tài khoản khách hàng / đối tác và ghi nhận bút toán kép (Double-entry Bookkeeping) chuẩn tài chính khi phát sinh giao dịch mua bán hoặc thanh toán.
Tự viết lại một hệ thống Core Banking / Kế toán tài chính từ đầu là rất tốn kém và tiềm ẩn nhiều rủi ro về chuẩn mực kế toán.

## Quyết định (Decision)
Tích hợp với nền tảng **Apache Fineract** (hệ thống mã nguồn mở chuẩn quốc tế cho Financial Services & Core Banking):
- Module `fineract` (`com.ddicg.erp.modules.fineract`) đóng vai trò là Gateway kết nối REST API sang Apache Fineract instance.
- **Cơ chế đồng bộ**: Sử dụng Kafka Listener để lắng nghe các sự kiện `OrderPlacedEvent`, `PaymentSuccessEvent` từ module `order`, sau đó tự động gọi API tạo Journal Entries và cập nhật số dư tài khoản trên Fineract.

## Các phương án cân nhắc (Alternatives Considered)

### 1. Tự thiết kế bảng Kế toán (Ledger tables) trong nội bộ ERP
- **Nhược điểm**: Khó đáp ứng đầy đủ các chuẩn mực đối soát kế toán tài chính quốc tế, mất nhiều tháng để implement và kiểm toán (audit).
- **Lý do từ chối**: Tận dụng giải pháp đã được chứng minh thực tế như Fineract giúp rút ngắn thời gian phát triển và đảm bảo tính tuân thủ.

## Hệ quả (Consequences)
- **Tích cực**:
  - Sở hữu năng lực hạch toán chuẩn ngân hàng/tài chính.
  - Phân tách rõ ràng giữa nghiệp vụ Thương mại (Bán hàng, Đơn hàng) và nghiệp vụ Kế toán/Ngân hàng.
  - Đồng bộ bất đồng bộ qua Kafka giúp luồng mua hàng của người dùng không bị phụ thuộc vào thời gian xử lý của Fineract.
