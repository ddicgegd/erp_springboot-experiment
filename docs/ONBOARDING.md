# Onboarding Guide: ERP Spring Boot Experiment System

## 1. Overview
Hệ thống ERP Spring Boot là nền tảng quản trị tài nguyên doanh nghiệp được kiến trúc theo mô hình **Modular Monolith** kết hợp **Event-Driven Architecture (EDA)**. Hệ thống quản lý toàn diện các luồng nghiệp vụ từ Định danh & Phân quyền (**IAM**), Danh mục hàng hóa & Tồn kho (**Merchandise**), Giỏ hàng (**Cart**), Xử lý đơn hàng & Thanh toán (**Order / VNPay**) đến Hạch toán sổ cái kế toán và tài khoản ngân hàng lõi (**Apache Fineract**).

---

## 2. Tech Stack

| Tầng / Thành phần | Công nghệ | Phiên bản | Vai trò & Mục đích |
| :--- | :--- | :--- | :--- |
| **Ngôn ngữ & Runtime** | Java | 21 (LTS) | Nền tảng thực thi chính |
| **Framework** | Spring Boot | 3.x | Web REST API, DI Container, Transaction Management |
| **Cơ sở dữ liệu chính** | Oracle Database XE | 21c | Lưu trữ dữ liệu cấu trúc nghiệp vụ (Users, Products, Orders) |
| **ORM / Data Access** | Spring Data JPA / Hibernate | 6.x | Entity Mapping, Repository, Transaction boundaries |
| **Caching & In-Memory** | Redis | Alpine | Quản lý giỏ hàng (Cart) với TTL và cache tốc độ cao |
| **Object Storage** | MinIO | S3-Compatible | Lưu trữ tệp nhị phân, hóa đơn PDF, ảnh sản phẩm |
| **Message Streaming** | Apache Kafka | 3.8 (KRaft) | Truyền nhận Domain Events bất đồng bộ với SASL_PLAINTEXT |
| **Core Banking / Ledger**| Apache Fineract | 1.x | Hạch toán sổ cái kế toán và tài khoản ngân hàng lõi |
| **Build & Packaging** | Apache Maven | 3.9+ (`mvnw`) | Quản lý dependencies và build artifact JAR |

---

## 3. Architecture & Module Map

### Cấu trúc thư mục mã nguồn (`src/main/java/com/ddicg/erp/`):
```text
com.ddicg.erp/
├── core/                         # Thành phần dùng chung toàn hệ thống
│   ├── common/                   # BaseEntity, ApiResponse, Pagination
│   ├── config/                   # Cấu hình Security, Redis, Kafka, Oracle, Swagger
│   ├── event/                    # BaseDomainEvent, KafkaPublisher, EventEnvelope
│   ├── exception/                # GlobalExceptionHandler, BusinessException, ErrorCode
│   └── security/                 # JWT Authentication Filter, UserPrincipal, SecurityConfig
└── modules/                      # Các miền nghiệp vụ độc lập (Domain Boundaries)
    ├── iam/                      # Identity & Access Management (User, Role, AuthController)
    ├── merchandise/              # Quản lý hàng hóa, Danh mục (Category), Thuộc tính (Attribute), Tồn kho
    ├── cart/                     # Quản lý giỏ hàng khách hàng lưu trữ trên Redis
    ├── order/                    # Quản lý đơn hàng (Order), Checkout, Tích hợp cổng VNPay
    └── fineract/                 # Tích hợp Apache Fineract (Core Banking Gateway, Ledger Sync)
```

---

## 4. Key Entry Points
* **Application Main**: `com.ddicg.erp.ErpApplication`
* **Security & Auth Filter**: `com.ddicg.erp.core.security.JwtAuthenticationFilter`
* **Global Error Handler**: `com.ddicg.erp.core.exception.GlobalExceptionHandler`
* **Kafka Event Dispatcher**: `com.ddicg.erp.core.event.KafkaDomainEventPublisher`
* **API Endpoints**:
  * `/api/v1/auth/**` — Đăng ký, Đăng nhập, Refresh token
  * `/api/v1/merchandises/**` — Quản lý sản phẩm & tồn kho
  * `/api/v1/cart/**` — Thêm, xóa, xem giỏ hàng
  * `/api/v1/orders/**` — Tạo đơn hàng, thanh toán, hủy đơn
  * `/api/v1/vnpay/**` — VNPay payment callback & IPN handler

---

## 5. Request Lifecycle (Vòng đời luồng xử lý Đơn hàng)

```
[Client / Swagger UI]
         │ (HTTP POST /api/v1/orders - JWT Token)
         ▼
[JwtAuthenticationFilter] ── (Xác thực JWT token & nạp UserPrincipal vào SecurityContext)
         │
         ▼
[OrderController] ── (Validate Request Body qua Bean Validation `@Valid`)
         │
         ▼
[OrderService] ── (Bắt đầu @Transactional)
         │
         ├──► [MerchandiseService] (Kiểm tra & giữ tồn kho - Stock Reservation)
         ├──► [OrderRepository] (Tạo bản ghi Order & OrderItems vào Oracle DB)
         │
         ▼ (Commit Transaction thành công)
[KafkaDomainEventPublisher] ── (Publish Domain Event sang Kafka: topic `order-placed-topic`)
         │
         ▼ (Bất đồng bộ)
[@KafkaListener trong FineractSyncModule]
         │
         ▼ (REST API Call)
[Apache Fineract Gateway] ── (Tự động tạo Journal Entries hạch toán kế toán)
```

---

## 6. Codebase Conventions

* **Naming Conventions**:
  * REST Controllers: `*Controller.java` (`@RestController`, `@RequestMapping("/api/v1/...")`)
  * Business Logic: `*Service.java` (Interface) và `*ServiceImpl.java` (Implementation với `@Service`)
  * Data Access: `*Repository.java` (Kế thừa `JpaRepository<Entity, Long>`)
  * Data Transfer: `*Request.java`, `*Response.java`, `*DTO.java`
* **Error Handling**:
  * Luôn ném `BusinessException(ErrorCode.XYZ)` khi phát hiện vi phạm nghiệp vụ.
  * `GlobalExceptionHandler` bắt và chuẩn hóa response về format `{ "success": false, "code": "...", "message": "..." }`.
* **Transaction Management**:
  * Đặt `@Transactional` tại tầng Service.
  * Các tác vụ I/O ngoài (gửi Kafka, upload MinIO, gọi REST Fineract) phải thực hiện sau khi DB transaction commit thành công.

---

## 7. Common Tasks (Lệnh thường dùng)

| Tác vụ | Lệnh thực thi |
| :--- | :--- |
| **Khởi động cụm hạ tầng** | `docker compose up -d` |
| **Dừng cụm hạ tầng** | `docker compose down` |
| **Chạy ứng dụng Backend** | `./mvnw clean spring-boot:run` |
| **Build file JAR** | `./mvnw clean package -DskipTests` |
| **Chạy toàn bộ Unit Tests** | `./mvnw test` |
| **Xem Swagger API UI** | Truy cập `http://localhost:8080/swagger-ui/index.html` |

---

## 8. Where to Look (Tra cứu nhanh khi cần phát triển)

| Khi tôi muốn... | Xem tại thư mục / file... |
| :--- | :--- |
| **Thêm API hoặc Endpoint mới** | `src/main/java/com/ddicg/erp/modules/<module>/controller/` |
| **Thêm logic nghiệp vụ** | `src/main/java/com/ddicg/erp/modules/<module>/service/` |
| **Thêm bảng / Entity mới** | `src/main/java/com/ddicg/erp/modules/<module>/entity/` |
| **Thêm mã lỗi nghiệp vụ mới** | `src/main/java/com/ddicg/erp/core/exception/ErrorCode.java` |
| **Cấu hình Kafka topic / Event mới** | `src/main/java/com/ddicg/erp/core/event/` |
| **Xem tài liệu REST API** | [docs/API_DOCUMENTATION.md](./API_DOCUMENTATION.md) |
| **Xem quyết định kiến trúc (ADRs)** | [docs/decisions/README.md](./decisions/README.md) |
