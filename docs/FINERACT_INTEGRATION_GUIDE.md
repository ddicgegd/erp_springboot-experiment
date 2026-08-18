# Hướng dẫn Tích hợp Core Banking & Sổ cái Apache Fineract

Tài liệu đặc tả kỹ thuật chi tiết về module `fineract` (`com.ddicg.erp.modules.fineract`) trong hệ thống ERP Spring Boot.

---

## 1. Tổng quan Kiến trúc Tích hợp (Integration Architecture)

Hệ thống ERP phân tách ranh giới rõ ràng giữa **Nghiệp vụ Thương mại (Bán hàng, Đơn hàng)** và **Nghiệp vụ Tài chính/Ngân hàng (Sổ cái, Tài khoản)**:

```
[Order Module] ──► (Publish Event: OrderPlacedEvent / PaymentSuccessEvent)
                         │
                         ▼ (Kafka Topic: order-placed-topic)
[Kafka Consumer: @KafkaListener trong FineractSyncService]
                         │
                         ▼ (REST API Gateway)
[Apache Fineract Instance: http://localhost:8080/fineract-provider/api/v1/...]
                         │
        ┌────────────────┴────────────────┐
        ▼                                 ▼
[General Ledger / Journal Entries]   [Client Accounts & Loans]
```

---

## 2. Các Thành phần Chính (Core Components)

1. **`FineractClientService`**:
   * Quản lý ánh xạ định danh người dùng giữa User trong ERP và Client ID trong Apache Fineract.
   * Phương thức `getOrCreateFineractClient(User user)`: Tự động kiểm tra và tạo mới khách hàng trên Fineract nếu chưa tồn tại.
2. **`FineractJournalService`**:
   * Quản lý ghi nhận bút toán kép (Double-entry Bookkeeping) chuẩn tài chính.
   * `recordSale(orderId, amount, note)`: Hạch toán Nợ (Debit) tài khoản Tiền/Thanh toán trung gian và Có (Credit) tài khoản Doanh thu bán hàng.
   * `recordRefund(orderId, amount, note)`: Hạch toán bút toán đảo khi đơn hàng bị hủy hoặc hoàn tiền.
3. **`FineractLoanProductService` & `FineractLoanService`**:
   * Cung cấp khả năng tích hợp sản phẩm tín dụng, vay trả góp cho các đơn hàng mua sắm giá trị lớn (BNPL - Buy Now Pay Later).

---

## 3. Danh sách Endpoints Gateway

| HTTP Method | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `POST` | `/api/v1/erp/accounting/sales` | Hạch toán doanh thu từ đơn hàng thành công |
| `POST` | `/api/v1/erp/accounting/refunds` | Hạch toán hoàn tiền / đảo bút toán |
| `GET` | `/api/v1/erp/clients` | Lấy danh sách khách hàng từ Fineract |
| `POST` | `/api/v1/erp/clients` | Tạo mới hồ sơ khách hàng |
| `POST` | `/api/v1/erp/clients/sync` | Đồng bộ tài khoản người dùng hiện tại sang Fineract |
| `GET` | `/api/v1/erp/loan-products` | Danh sách gói sản phẩm vay / trả góp |
| `POST` | `/api/v1/erp/loans` | Tạo hồ sơ khoản vay liên kết với đơn hàng |

---

## 4. Xử lý Lỗi & Cơ chế Bù trừ (Error Handling & Reconciliation)

* **Bất đồng bộ & Idempotency**: Mọi message Kafka xử lý hạch toán đều gắn `orderNumber` làm khóa đối soát (Idempotency Key) để tránh hạch toán trùng lặp khi Kafka re-deliver message.
* **Dead Letter Topic (DLT)**: Các sự kiện hạch toán lỗi (do Fineract downtime) sẽ được đẩy vào topic `fineract-accounting-dlt` để retry tự động hoặc xử lý thủ công qua Dashboard đối soát.
