# TÀI LIỆU THIẾT KẾ KỸ THUẬT: PHÂN HỆ TÍCH HỢP APACHE FINERACT (ERP SUBSYSTEM)

> **Target Audience (Đối tượng đọc):** AI Agent (`mục tiêu đọc: agent`)  
> **Tài liệu chuẩn hóa:** Đặc tả kỹ thuật, hợp đồng dữ liệu (Data Contracts), máy trạng thái (State Machine), quy tắc kế toán kép (Double-entry Rules), tích hợp EDA Kafka, và sổ tay hướng dẫn thực thi tự động (Agent Execution Runbook).  
> **Workspace Context:** `com.ddicg.erp.modules.fineract`

---

## 1. TỔNG QUAN KIẾN TRÚC & TÔPÔ TÍCH HỢP (System Architecture & Topology)

Phân hệ Fineract đóng vai trò là **Core Banking & Financial Ledger Gateway** của hệ thống ERP Modular Monolith. Phân hệ kết nối giữa dữ liệu nghiệp vụ thương mại (IAM, Order, Payment) với hệ thống lõi ngân hàng/sổ cái Apache Fineract thông qua 2 cơ chế tương tác:

```
┌───────────────────────────────────────────────────────────────────────────────────────┐
│                               ERP Monolith Application                                │
│                                                                                       │
│  ┌─────────────────────────┐                 ┌─────────────────────────────────────┐  │
│  │   Module: IAM (User)    │                 │        Module: Order (Checkout)     │  │
│  └───────────┬─────────────┘                 └──────────────────┬──────────────────┘  │
│              │ getOrCreateFineractClient                        │ Domain Event        │
│              ▼                                                  ▼                     │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐  │
│  │                           Module: Fineract Gateway                              │  │
│  │                                                                                 │  │
│  │  [FineractClientService]      [FineractLoanService]     [FineractJournalService]│  │
│  │             │                            │                          ▲           │  │
│  │             └───────────────┬────────────┘                          │           │  │
│  │                             │                                       │           │  │
│  │                             ▼                                       │           │  │
│  │                [Spring RestClient + SSL Bypass]                     │           │  │
│  │             (Basic Auth + Fineract Tenant Header)                   │           │  │
│  └─────────────────────────────┼───────────────────────────────────────┼───────────┘  │
│                                │ HTTP/REST                             │              │
└────────────────────────────────┼───────────────────────────────────────┼──────────────┘
                                 │                                       │
                                 │                             ┌─────────┴──────────┐
                                 │                             │ OrderFineractConsumer│
                                 │                             │ (Kafka @KafkaListener)
                                 │                             └─────────▲──────────┘
                                 ▼                                       │
                ┌───────────────────────────────────┐                    │
                │     Apache Fineract Core API      │                    │ Kafka Broker
                │   (Multi-tenant Core Banking)     │                    │ Topic: order-topic
                │  - Clients Management             │                    │ Group: fineract-order-group
                │  - Loan Products & Portfolio      │                    │
                │  - General Ledger (Double-Entry)  │                    │
                └───────────────────────────────────┘                    │
                                 ▲                                       │
                                 └───────────────────────────────────────┘
```

### 1.1. Hạ tầng giao thức & Cấu hình kết nối

| Tham số cấu hình | Thuộc tính Java | Biến môi trường | Giá trị mặc định (Dev) | Ý nghĩa nghiệp vụ |
| :--- | :--- | :--- | :--- | :--- |
| **Base URL** | `fineract.baseUrl` | `FINERACT_BASE_URL` | `https://localhost:8443/fineract-provider/api/v1` | Điểm cuối REST API của Fineract Core |
| **Tenant ID** | `fineract.tenantId` | `FINERACT_TENANT_ID` | `default` | Header bắt buộc `Fineract-Platform-TenantId` định danh tenant |
| **Username** | `fineract.username` | `FINERACT_USERNAME` | `mifos` | Basic Auth user quản trị Fineract |
| **Password** | `fineract.password` | `FINERACT_PASSWORD` | `password` | Basic Auth password |
| **Office ID** | `fineract.officeId` | `FINERACT_OFFICE_ID` | `1` | ID chi nhánh mặc định (Head Office) |
| **Legal Form** | `fineract.legalFormId` | `FINERACT_LEGAL_FORM_ID` | `1` | Thể nhân cá nhân (`1 = Person`, `2 = Entity`) |
| **Date Format** | `fineract.dateFormat` | `FINERACT_DATE_FORMAT` | `dd MMMM yyyy` | Định dạng ngày bắt buộc của Fineract payload |
| **Locale** | `fineract.locale` | `FINERACT_LOCALE` | `en` | Ngôn ngữ xử lý định dạng số/ngày |
| **SSL Bypass** | `fineract.sslBypass` | `FINERACT_SSL_BYPASS` | `true` | Bỏ qua xác thực chứng chỉ TLS self-signed ở dev |
| **Currency** | `fineract.currencyCode`| `FINERACT_CURRENCY_CODE` | `VND` | Đơn vị tiền tệ hạch toán sổ cái |
| **Feature Flag**| `app.fineract.enabled` | `APP_FINERACT_ENABLED` | `true` | Cờ bật/tắt toàn bộ Consumer tích hợp nền |

### 1.2. Invariant & Giao thức RestClient

1. **Header Injection:** Mọi request từ ERP sang Fineract thông qua bean `fineractRestClient` đều tự động đính kèm:
   - `Authorization: Basic <base64(username:password)>`
   - `Fineract-Platform-TenantId: <tenantId>`
   - `Content-Type: application/json`
2. **Fail-safe & Exception Handling:** Nếu Fineract trả về lỗi HTTP 4xx/5xx, `FineractExceptionHandler` bắt `RestClientResponseException`, trích xuất chuỗi JSON gốc `fineractResponse` đưa vào phản hồi client để agent có thể đọc và phân tích trực tiếp nguyên nhân nghiệp vụ core banking.

---

## 2. QUẢN LÝ KHÁCH HÀNG TÀI CHÍNH (Client Subsystem)

### 2.1. Chu trình Đồng bộ Định danh (Identity Mapping & Idempotency)

Fineract định danh khách hàng bằng `Long clientId`, trong khi ERP định danh bằng `Long userId` (String `externalId` trên Fineract).

```
[Agent / Request]
       │
       ▼
[FineractClientService.getOrCreateFineractClient(User)]
       │
       ├─► Đã có user.getFineractClientId()?
       │      ├─ Có ──► [Trả về clientId ngay] (Không gọi mạng)
       │      └─ Không
       │
       ├─► Phân tích họ tên qua VietnameseNameParser.parse(user.fullName)
       │
       ├─► Tra cứu Idempotency: GET /clients?externalId={userId}
       │      ├─ Đã tồn tại ──► Cập nhật user.setFineractClientId(id) ──► Save DB ──► [Trả về clientId]
       │      └─ Chưa có
       │
       └─► Khởi tạo mới: POST /clients
              ├─ externalId: String.valueOf(userId)
              ├─ firstname, lastname: từ ParsedName
              ├─ email, mobileNo, officeId, legalFormId
              └─ Thành công ──► user.setFineractClientId(response.clientId) ──► Save DB ──► [Trả về clientId]
```

### 2.2. Hợp đồng API Client

#### Endpoint: `GET /api/v1/erp/clients`
- **Mục đích:** Lấy danh sách khách hàng Fineract.
- **RBAC Logic:**
  - Nếu `ADMIN`, `MANAGEMENT`, `STAFF`: Gọi upstream `GET /clients` (Lấy toàn bộ khách hàng trên Core Banking).
  - Nếu `CUSTOMER` (người dùng thông thường): Lấy `userId` từ JWT token, gọi upstream `GET /clients?externalId={userId}` (Chỉ lấy hồ sơ của chính mình).
- **Phản hồi (200 OK):**
```json
{
  "totalFilteredRecords": 1,
  "pageItems": [
    {
      "id": 105,
      "accountNo": "000000105",
      "status": {
        "id": 300,
        "code": "clientStatusType.active",
        "value": "Active"
      },
      "active": true,
      "activationDate": [2026, 9, 4],
      "firstname": "Văn A",
      "lastname": "Nguyễn",
      "displayName": "Nguyễn Văn A",
      "officeId": 1,
      "officeName": "Head Office",
      "externalId": "42"
    }
  ]
}
```

#### Endpoint: `GET /api/v1/erp/clients/{clientId}`
- **Mục đích:** Xem chi tiết hồ sơ tài chính của một client cụ thể theo ID.
- **Phản hồi (200 OK):** Object chi tiết bao gồm thông tin định danh, tài khoản liên kết, và trạng thái.

#### Endpoint: `POST /api/v1/erp/clients`
- **Mục đích:** Chủ động đăng ký hồ sơ khách hàng mới lên Fineract.
- **Request Body (JSON):**
```json
{
  "officeId": 1,
  "legalFormId": 1,
  "firstname": "Văn A",
  "lastname": "Nguyễn",
  "externalId": "42",
  "emailAddress": "nguyenvana@example.com",
  "mobileNo": "0912345678",
  "active": true,
  "activationDate": "04 September 2026",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en"
}
```
- **Phản hồi thành công (200 OK):**
```json
{
  "officeId": 1,
  "clientId": 105,
  "resourceId": 105
}
```

---

## 3. SẢN PHẨM VÀ VÒNG ĐỜI KHOẢN VAY (Loan & Loan Product Subsystem)

### 3.1. Danh mục Gói sản phẩm vay (Loan Products)

#### Endpoint: `GET /api/v1/erp/loan-products`
- **Mục đích:** Truy vấn các gói tín dụng/vay trả góp đang mở trên Core Banking để hiển thị hoặc lựa chọn khi tạo hồ sơ vay.
- **Upstream Call:** `GET /loanproducts`
- **Phản hồi mẫu:**
```json
[
  {
    "id": 1,
    "name": "Vay Trả Góp Mua Hàng ERP",
    "shortName": "ERP-LN01",
    "currency": {
      "code": "VND",
      "name": "Vietnamese Dong",
      "decimalPlaces": 0
    },
    "principal": 5000000.0,
    "minPrincipal": 1000000.0,
    "maxPrincipal": 50000000.0,
    "numberOfRepayments": 12,
    "repaymentEvery": 1,
    "interestRatePerPeriod": 1.5,
    "annualInterestRate": 18.0
  }
]
```

### 3.2. Ma trận Trạng thái Khoản vay (Loan Lifecycle State Machine)

Khoản vay trong Fineract vận hành theo một cỗ máy trạng thái nghiêm ngặt (Strict State Transitions):

```
       ┌──────────────────────┐
       │   [Khởi tạo đơn]     │
       │   POST /erp/loans    │
       └──────────┬───────────┘
                  │
                  ▼
       ┌──────────────────────┐
       │  100: PENDING_       │────── Command: reject ────────► [500: REJECTED]
       │       APPROVAL       │
       └──────────┬───────────┘────── Command: withdrawnBy ────► [400: WITHDRAWN]
                  │
                  │ Command: approve (POST /loans/{id}/approve)
                  ▼
       ┌──────────────────────┐
       │    200: APPROVED     │
       └──────────┬───────────┘
                  │
                  │ Command: disburse (POST /loans/{id}/disburse)
                  ▼
       ┌──────────────────────┐
       │     300: ACTIVE      │◄────────────────────────┐
       └──────────┬───────────┘                         │
                  │                                     │ Trả góp từng kỳ
                  │ Transaction: repayment              │ (Outstanding > 0)
                  │ (POST /loans/{id}/repayments)       │
                  ▼                                     │
         [Số dư nợ = 0 ?] ─── Không ────────────────────┘
                  │
                  │ Có (Đã tất toán toàn bộ gốc & lãi)
                  ▼
       ┌──────────────────────┐
       │ 600: OBLIGATIONS_MET │ (Khoản vay hoàn tất)
       └──────────────────────┘
```

### 3.3. Hợp đồng API Quản lý Khoản vay (Loan APIs)

#### 1. `GET /api/v1/erp/loans`
- **Quyền hạn:** 
  - Staff / Admin: Xem danh sách toàn bộ khoản vay trong hệ thống.
  - Khách hàng: Hệ thống tự động phân giải `clientId` của người dùng đang đăng nhập và lọc `GET /loans?clientId={clientId}`. Nếu chưa có hồ sơ client, trả về danh sách rỗng an toàn `{"totalFilteredRecords": 0, "pageItems": []}`.

#### 2. `GET /api/v1/erp/loans/{loanId}`
- **Mục đích:** Xem chi tiết hợp đồng vay, bao gồm cấu trúc dư nợ gốc, tiền lãi, phí, và toàn bộ bảng lịch trả nợ từng kỳ (`repaymentSchedule`).
- **Upstream Call:** `GET /loans/{loanId}?associations=all`

#### 3. `GET /api/v1/erp/loans/template?productId={productId}`
- **Mục đích:** Lấy mẫu thông số khởi tạo hồ sơ vay theo một mã sản phẩm cụ thể. Tự động gắn kèm `clientId` của phiên người dùng hiện tại để Fineract tính toán hạn mức phù hợp.

#### 4. `POST /api/v1/erp/loans`
- **Mục đích:** Nộp đơn xin vay vốn mới (Submit Loan Application).
- **Auto-enrichment Invariant:** Nếu trong JSON payload gửi lên chưa có `clientId`, `FineractLoanService` sẽ **tự động phân giải hoặc kích hoạt tạo Client Fineract** từ tài khoản User đăng nhập và tự động bổ sung trường `"clientId": <id>` vào payload trước khi gửi sang Fineract.
- **Request Body (JSON):**
```json
{
  "clientId": 105,
  "productId": 1,
  "principal": 10000000,
  "loanTermFrequency": 6,
  "loanTermFrequencyType": 2,
  "numberOfRepayments": 6,
  "repaymentEvery": 1,
  "repaymentFrequencyType": 2,
  "interestRatePerPeriod": 1.2,
  "amortizationType": 1,
  "interestType": 0,
  "interestCalculationPeriodType": 1,
  "expectedDisbursementDate": "04 September 2026",
  "submittedOnDate": "04 September 2026",
  "transactionProcessingStrategyCode": "mifos-standard-strategy",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en"
}
```
*(Ghi chú kiểu tần suất: `2 = Tháng / Months`)*

#### 5. Các lệnh Chuyển trạng thái Vòng đời (State Transition Endpoints)

Mọi endpoint chuyển trạng thái đều nhận body tùy chọn (hoặc tự động dùng JSON rỗng nếu client không truyền):

| Lệnh nghiệp vụ | Endpoint ERP | Upstream Fineract URL | Yêu cầu dữ liệu trong Payload |
| :--- | :--- | :--- | :--- |
| **Phê duyệt vay** | `POST /api/v1/erp/loans/{id}/approve` | `/loans/{id}?command=approve` | `{"approvedOnDate": "04 September 2026", "dateFormat": "dd MMMM yyyy", "locale": "en"}` |
| **Giải ngân vốn** | `POST /api/v1/erp/loans/{id}/disburse`| `/loans/{id}?command=disburse` | `{"actualDisbursementDate": "04 September 2026", "dateFormat": "dd MMMM yyyy", "locale": "en"}` |
| **Từ chối đơn vay**| `POST /api/v1/erp/loans/{id}/reject` | `/loans/{id}?command=reject` | `{"rejectedOnDate": "04 September 2026", "dateFormat": "dd MMMM yyyy", "locale": "en"}` |
| **Khách rút đơn** | `POST /api/v1/erp/loans/{id}/withdraw`| `/loans/{id}?command=withdrawnByApplicant` | `{"withdrawnOnDate": "04 September 2026", "dateFormat": "dd MMMM yyyy", "locale": "en"}` |
| **Thanh toán nợ** | `POST /api/v1/erp/loans/{id}/repayments` | `/loans/{id}/transactions?command=repayment` | `{"transactionDate": "04 September 2026", "transactionAmount": 1800000, "dateFormat": "dd MMMM yyyy", "locale": "en"}` |

---

## 4. HỆ THỐNG SỔ CÁI KẾ TOÁN KÉP (General Ledger & Double-Entry Invariants)

### 4.1. Định luật Kế toán kép (Double-Entry Invariant)

Mọi bút toán sổ cái gửi sang Fineract thông qua phân hệ ERP bắt buộc phải tuân thủ nghiêm ngặt bất biến:

$$\sum_{i=1}^{n} \text{Debit}_i \equiv \sum_{j=1}^{m} \text{Credit}_j$$

Nếu $\sum \text{Debit} \neq \sum \text{Credit}$, `FineractJournalService` sẽ ném ngay `IllegalArgumentException` trước khi phát sinh bất kỳ kết nối mạng nào ra ngoài.

### 4.2. Ma trận Phân giải Tài khoản Sổ cái (GL Account Resolver Matrix)

Lớp `GlAccountResolver` đảm nhiệm việc ánh xạ các phương thức thanh toán của đơn hàng thành các mã tài khoản kế toán trong Fineract:

| Nghiệp vụ ERP | Phương thức thanh toán (`PaymentMethod`) | Tài khoản Nợ (Debit Account) | Tài khoản Có (Credit Account) | Mã tham chiếu (`referenceNumber`) |
| :--- | :--- | :--- | :--- | :--- |
| **Bán hàng thành công (SALE)** | `COD` (Tiền mặt khi giao) | **TK Tiền mặt** (`cashGlAccountId` = 1) | **TK Doanh thu bán hàng** (`salesRevenueGlAccountId` = 2) | `SALE-{orderNumber}` |
| **Bán hàng thành công (SALE)** | `VNPAY`, `BANK_TRANSFER`, `CREDIT_CARD`, `MOMO`, `PAYPAL` | **TK Ngân hàng** (`bankGlAccountId` = 4) | **TK Doanh thu bán hàng** (`salesRevenueGlAccountId` = 2) | `SALE-{orderNumber}` |
| **Hoàn tiền trả hàng (REFUND)** | `COD` | **TK Hàng bán bị trả lại** (`salesReturnsGlAccountId` = 3) | **TK Tiền mặt** (`cashGlAccountId` = 1) | `REFUND-{orderNumber}` |
| **Hoàn tiền trả hàng (REFUND)** | `VNPAY`, `BANK_TRANSFER`, `CREDIT_CARD`, `MOMO`, `PAYPAL` | **TK Hàng bán bị trả lại** (`salesReturnsGlAccountId` = 3) | **TK Ngân hàng** (`bankGlAccountId` = 4) | `REFUND-{orderNumber}` |

*(Lưu ý cho Agent: Nếu `bankGlAccountId` chưa được cấu hình, hệ thống tự động fallback về `cashGlAccountId`).*

### 4.3. Hợp đồng API Bút toán Sổ cái

#### 1. `GET /api/v1/erp/journalentries`
- **Mục đích:** Truy vấn danh sách toàn bộ các bút toán đã hạch toán trong hệ thống Core Banking.
- **Upstream Call:** `GET /journalentries`

#### 2. `POST /api/v1/erp/journalentries`
- **Mục đích:** Ghi thủ công một bút toán kế toán kép.
- **Request Schema (`JournalEntryRequestDTO`):**
```json
{
  "officeId": 1,
  "transactionDate": "04 September 2026",
  "currencyCode": "VND",
  "referenceNumber": "MANUAL-TX-9901",
  "comments": "Bút toán điều chỉnh chi phí phát sinh",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en",
  "debits": [
    {
      "glAccountId": 1,
      "amount": 500000.00
    }
  ],
  "credits": [
    {
      "glAccountId": 2,
      "amount": 500000.00
    }
  ]
}
```
- **Ràng buộc kiểm thực (Validation Constraints):**
  - `officeId`: Not null.
  - `transactionDate`, `currencyCode`, `referenceNumber`: Not blank.
  - `debits`, `credits`: Không được rỗng (`@NotEmpty`), các phần tử con phải có `glAccountId` và `amount > 0`.
  - $\sum \text{amount}_{\text{debits}} == \sum \text{amount}_{\text{credits}}$.

---

## 5. TÍCH HỢP BẤT ĐỒNG BỘ QUA KAFKA (EDA Order-to-Ledger Synchronization)

### 5.1. Cấu hình & Kênh lắng nghe (Consumer Topology)

Thành phần `OrderFineractConsumer` chạy ngầm để lắng nghe các thay đổi của đơn hàng từ module Order:

- **Topic:** `order-topic` (được định nghĩa tại `KafkaTopics.ORDER_TOPIC`)
- **Consumer Group ID:** `fineract-order-group`
- **Offset Reset Policy:** `earliest`
- **Auto Commit:** `false` (Quản lý cam kết thủ công đảm bảo At-Least-Once Delivery)
- **Kích hoạt có điều kiện:** `@ConditionalOnProperty(name = "app.fineract.enabled", havingValue = "true")`

### 5.2. Cấu trúc Message & Ma trận Kích hoạt Hạch toán

Agent cần định dạng hoặc phân tích message từ `order-topic` theo cấu trúc sau:

```json
{
  "eventType": "ORDER_STATUS_CHANGED",
  "orderNumber": "ORD-20260904-9876",
  "orderId": "ORD-20260904-9876",
  "newStatus": "PROCESSING",
  "previousStatus": "PENDING"
}
```

| Event Type | Trường kiểm tra | Giá trị điều kiện | Hành động hạch toán Fineract | Mã tham chiếu sổ cái sinh ra |
| :--- | :--- | :--- | :--- | :--- |
| `ORDER_STATUS_CHANGED` | `newStatus` | `PROCESSING` | `recordSale(orderNumber, totalAmount, ...)` | `SALE-{orderNumber}` |
| `ORDER_STATUS_CHANGED` | `newStatus` | `REFUNDED` | `recordRefund(orderNumber, totalAmount, ...)` | `REFUND-{orderNumber}` |
| `ORDER_CREATED` | `initialStatus` | `PROCESSING` | `recordSale(orderNumber, totalAmount, ...)` | `SALE-{orderNumber}` |
| Các trạng thái khác | `newStatus` | `DELIVERED`, `CANCELLED`, `COMPLETED` | **Ignored** (Bỏ qua, không phát sinh hạch toán kép) | Không |

### 5.3. Xử lý lỗi & Tự phục hồi trong Consumer

1. Khi nhận message, consumer truy vấn lại thông tin đơn hàng trong database cục bộ qua `orderRepository.findByOrderNumber(orderNumber)` để lấy số tiền chính xác (`totalAmount`).
2. Nếu không tìm thấy Order hoặc `totalAmount <= 0`, consumer ghi cảnh báo `WARN` và hủy bỏ xử lý bút toán mà không gây sập consumer thread.
3. Nếu Fineract core API từ chối ghi nhận hoặc xảy ra sự cố mạng, lỗi được bắt tại block `try-catch`, log ra dạng `ERROR` kèm stacktrace chi tiết để phục vụ tái xử lý (Dead-letter reconciliation).

---

## 6. DANH MỤC LỖI & CHIẾN LƯỢC XỬ LÝ SỰ CỐ (Error Handling & Fault Protocol)

Mọi lỗi trả về từ phân hệ Fineract Controller đều tuân theo chuẩn định dạng JSON của `FineractExceptionHandler`:

### 6.1. Lỗi từ Core Banking Fineract (RestClientResponseException)
Khi Fineract từ chối request (ví dụ: ngày thanh toán không hợp lệ, khoản vay chưa được duyệt, tài khoản không đủ số dư):
- **HTTP Status Code:** Nhận đúng mã trạng thái từ Fineract trả về (400, 403, 404, 500).
- **Cấu trúc JSON phản hồi:**
```json
{
  "status": "error",
  "httpStatusCode": 403,
  "message": "Lỗi từ hệ thống Core Banking (Fineract).",
  "fineractResponse": "{\"developerMessage\":\"The date on which a loan is approved cannot be in the future.\",\"httpStatusCode\":\"403\",\"defaultUserMessage\":\"The date on which a loan is approved cannot be in the future.\",\"userMessageGlobalisationCode\":\"error.msg.loan.approval.cannot.be.in.the.future\",\"parameterName\":\"approvedOnDate\"}"
}
```
> **Chỉ dẫn cho AI Agent:** Hãy phân tích chuỗi JSON bên trong trường `fineractResponse` để trích xuất `userMessageGlobalisationCode` và `developerMessage`, từ đó đưa ra quyết định khắc phục tham số tự động.

### 6.2. Lỗi Validation tham số đầu vào (MethodArgumentNotValidException)
- **HTTP Status Code:** `400 BAD REQUEST`
- **Cấu trúc JSON phản hồi:**
```json
{
  "status": "error",
  "message": "Dữ liệu đầu vào không hợp lệ",
  "fieldErrors": {
    "credits": "At least one credit entry is required",
    "transactionDate": "Transaction date is required"
  }
}
```

### 6.3. Lỗi nội bộ hệ thống ERP (Generic Exception)
- **HTTP Status Code:** `500 INTERNAL SERVER ERROR`
- **Cấu trúc JSON phản hồi:**
```json
{
  "status": "error",
  "message": "Lỗi hệ thống nội bộ ERP: Nguyên tắc kế toán kép vi phạm: Tổng Nợ (500000) khác Tổng Có (400000)"
}
```

---

## 7. SỔ TAY THỰC THI DÀNH CHO AGENT (Agent Operational Runbook)

Dưới đây là các kịch bản hành động chuẩn (standard workflows) mà một Agent cần tuân thủ khi thực thi các tác vụ liên quan đến Fineract:

### Kịch bản 1: Đăng ký & Xin khoản vay mới cho Người dùng
1. **Bước 1:** Lấy thông tin user hiện tại bằng cách gọi `GET /api/v1/auth/me` hoặc trích xuất từ User Context.
2. **Bước 2:** Gọi `GET /api/v1/erp/loan-products` để lấy danh sách gói sản phẩm. Chọn một `productId` thỏa mãn nhu cầu (ví dụ: `productId = 1`).
3. **Bước 3:** Gọi `GET /api/v1/erp/loans/template?productId=1` để trích xuất các thông số ràng buộc: `minPrincipal`, `maxPrincipal`, `interestRatePerPeriod`.
4. **Bước 4:** Gửi `POST /api/v1/erp/loans`. 
   - *Lưu ý:* Không bắt buộc phải truyền `clientId` trong body nếu đang gọi dưới quyền User token (hệ thống sẽ tự động gọi `getOrCreateCurrentClient`).
   - Định dạng ngày tháng bắt buộc là `dd MMMM yyyy` (ví dụ: `04 September 2026`).

### Kịch bản 2: Duyệt và Giải ngân Khoản vay (Dành cho Agent có quyền Admin/Staff)
1. **Bước 1:** Kiểm tra chi tiết hồ sơ bằng cách gọi `GET /api/v1/erp/loans/{loanId}`. Xác nhận trạng thái `status.id == 100` (`PENDING_APPROVAL`).
2. **Bước 2 (Approve):** Gửi `POST /api/v1/erp/loans/{loanId}/approve` với payload:
   ```json
   {
     "approvedOnDate": "<Ngày hiện tại dạng dd MMMM yyyy>",
     "dateFormat": "dd MMMM yyyy",
     "locale": "en"
   }
   ```
3. **Bước 3 (Disburse):** Gửi `POST /api/v1/erp/loans/{loanId}/disburse` với payload:
   ```json
   {
     "actualDisbursementDate": "<Ngày hiện tại dạng dd MMMM yyyy>",
     "dateFormat": "dd MMMM yyyy",
     "locale": "en"
   }
   ```
4. **Bước 4:** Kiểm tra lại trạng thái qua `GET /api/v1/erp/loans/{loanId}`, xác nhận trạng thái chuyển sang `status.id == 300` (`ACTIVE`).

### Kịch bản 3: Thực hiện Thanh toán kỳ trả góp (Repayment)
1. **Bước 1:** Gọi `GET /api/v1/erp/loans/{loanId}`, đọc mảng `repaymentSchedule.periods` để tìm kỳ đến hạn có `complete == false`.
2. **Bước 2:** Lấy số tiền cần trả `totalDueForPeriod`.
3. **Bước 3:** Gửi `POST /api/v1/erp/loans/{loanId}/repayments`:
   ```json
   {
     "transactionDate": "<Ngày trả dạng dd MMMM yyyy>",
     "transactionAmount": <Số tiền>,
     "dateFormat": "dd MMMM yyyy",
     "locale": "en"
   }
   ```

### Kịch bản 4: Hạch toán Doanh thu / Hoàn tiền thủ công cho Đơn hàng đặc biệt
1. **Bước 1:** Xác định loại giao dịch (`SALE` hay `REFUND`) và phương thức thanh toán của đơn hàng.
2. **Bước 2:** Xác định ID tài khoản theo quy tắc:
   - Nếu thanh toán tiền mặt/COD: Tài khoản tài sản = `1` (`cashGlAccountId`).
   - Nếu thanh toán cổng điện tử/ngân hàng: Tài khoản tài sản = `4` (`bankGlAccountId`).
   - Doanh thu bán hàng = `2` (`salesRevenueGlAccountId`).
   - Hàng bán trả lại = `3` (`salesReturnsGlAccountId`).
3. **Bước 3:** Kiểm tra đảm bảo cân bằng $\sum \text{Debit} == \sum \text{Credit}$.
4. **Bước 4:** Gửi `POST /api/v1/erp/journalentries`.
