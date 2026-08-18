# ERP Spring Boot Experiment System

Hệ thống quản trị tài nguyên doanh nghiệp (**Enterprise Resource Planning - ERP**) xây dựng trên kiến trúc **Modular Monolith** kết hợp **Event-Driven Architecture (EDA)** với **Spring Boot 3.x** và **Java 21**.

---

## 🏗️ Kiến trúc Tổng thể (System Architecture)

Hệ thống được thiết kế phân tách theo ranh giới miền nghiệp vụ (Domain Boundaries), kết nối linh hoạt giữa tính nhất quán giao dịch ACID và khả năng xử lý sự kiện bất đồng bộ:

```
                  ┌─────────────────────────────────────────┐
                  │          Client / Swagger UI            │
                  └────────────────────┬────────────────────┘
                                       │ HTTP / REST
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Spring Boot Application                               │
│                                                                                 │
│  ┌───────────────────┐  ┌───────────────────────┐  ┌─────────────────────────┐  │
│  │   Module: IAM     │  │  Module: Merchandise  │  │      Module: Cart       │  │
│  │ (User/Role/Auth)  │  │ (Catalog & Inventory) │  │    (Redis Caching)      │  │
│  └───────────────────┘  └───────────┬───────────┘  └────────────┬────────────┘  │
│                                     │                           │               │
│                                     ▼                           ▼               │
│                         ┌───────────────────────────────────────────┐           │
│                         │               Module: Order               │           │
│                         │      (Order Lifecycle & Checkout)         │           │
│                         └─────────────────────┬─────────────────────┘           │
│                                               │                                 │
│                                               ▼ (Domain Event)                  │
│                                    [Kafka Producer: KafkaTemplate]              │
└───────────────────────────────────────────────┼─────────────────────────────────┘
                                                │ SASL_PLAINTEXT
                                                ▼
                                    ┌───────────────────────┐
                                    │      Apache Kafka     │
                                    │     (KRaft Broker)    │
                                    └───────────┬───────────┘
                                                │ Event: order-placed-topic
                                                ▼
┌───────────────────────────────────────────────┴─────────────────────────────────┐
│                             Module: Fineract Sync                               │
│                         [Kafka Consumer: @KafkaListener]                        │
│                                       │                                         │
│                                       ▼ REST Gateway                            │
│                        ┌──────────────────────────────┐                         │
│                        │   Apache Fineract Core API   │                         │
│                        │  (Sổ cái & Tài khoản Banking)│                         │
│                        └──────────────────────────────┘                         │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Công nghệ & Hạ tầng (Technology Stack)

| Thành phần | Công nghệ / Phiên bản | Vai trò trong hệ thống |
| :--- | :--- | :--- |
| **Backend Core** | Java 21, Spring Boot 3.x, Spring Data JPA | Nền tảng ứng dụng và xử lý nghiệp vụ chính |
| **Primary Database** | Oracle Database XE 21c (`gvenzl/oracle-xe`) | Lưu trữ dữ liệu cấu trúc quan hệ (Users, Products, Orders) |
| **Caching & Session** | Redis Alpine (`redis:alpine`) | Lưu trữ giỏ hàng (Cart) và caching tốc độ cao |
| **Object Storage** | MinIO (`minio/minio`) | Lưu trữ file nhị phân (Ảnh sản phẩm, Hóa đơn PDF) S3-compatible |
| **Message Broker** | Apache Kafka 3.8 (`bitnami/kafka`) | Truyền nhận Domain Events (KRaft mode, SASL Authentication) |
| **Core Banking / Ledger**| Apache Fineract | Hạch toán sổ cái kế toán và tài khoản ngân hàng lõi |
| **API Docs** | SpringDoc OpenAPI / Swagger 3 | Tài liệu hóa và kiểm thử RESTful API tương tác |

---

## 📦 Cấu trúc Thư mục & Modules

```text
erp_springboot-experiment/
├── docs/                        # Tài liệu kỹ thuật chi tiết
│   ├── decisions/               # Danh mục Architecture Decision Records (ADRs)
│   ├── notebooklm-context/      # Tài liệu tổng hợp tối ưu cho AI RAG / NotebookLM
│   ├── features/                # Specs và báo cáo chi tiết từng tính năng
│   └── API_DOCUMENTATION.md     # Danh mục đầy đủ hợp đồng REST API
├── src/main/java/com/ddicg/erp/
│   ├── core/                    # Thành phần dùng chung toàn hệ thống
│   │   ├── common/              # Base Entity, DTO wrapper, Utilities
│   │   ├── config/              # Cấu hình Security, Redis, Kafka, Oracle
│   │   ├── event/               # Base Domain Events & Kafka Publisher
│   │   ├── exception/           # GlobalExceptionHandler & BusinessException
│   │   └── security/            # JWT Filter, UserPrincipal
│   └── modules/                 # Các miền nghiệp vụ độc lập
│       ├── iam/                 # Xác thực, Phân quyền & Quản lý người dùng
│       ├── merchandise/         # Danh mục hàng hóa, Thuộc tính, Tồn kho
│       ├── cart/                # Quản lý giỏ hàng trên Redis
│       ├── order/               # Đơn hàng, Thanh toán VNPay & Checkout
│       └── fineract/            # Tích hợp hạch toán kế toán Apache Fineract
└── docker-compose.yml           # Khởi chạy toàn bộ cụm hạ tầng phụ trợ
```

---

## 🚀 Hướng dẫn Khởi chạy (Quick Start)

### 1. Yêu cầu môi trường
* Java Development Kit (**JDK 21**)
* **Docker** & **Docker Compose**
* Maven Wrapper (sẵn có trong repo: `./mvnw`)

### 2. Khởi chạy cụm dịch vụ hạ tầng
Khởi động Oracle Database, Redis, MinIO, và Kafka KRaft:
```bash
docker compose up -d
```

*Kiểm tra trạng thái container:*
```bash
docker compose ps
```

### 3. Khởi chạy Ứng dụng Spring Boot
```bash
./mvnw clean spring-boot:run
```

* Ứng dụng chạy mặc định tại: `http://localhost:8080`
* Tài liệu Swagger UI: `http://localhost:8080/swagger-ui/index.html`

---

## 📖 Tài liệu Chi tiết & Quyết định Kiến trúc

* 🏛️ **[Architecture Decision Records (ADRs)](./docs/decisions/README.md)**:
  * [ADR-001: Lựa chọn kiến trúc Modular Monolith](./docs/decisions/ADR-001-modular-monolith-architecture.md)
  * [ADR-002: Cơ sở dữ liệu chính Oracle XE & JPA](./docs/decisions/ADR-002-primary-database-oracle-jpa.md)
  * [ADR-003: Event-Driven Messaging với Kafka KRaft SASL](./docs/decisions/ADR-003-event-driven-messaging-kafka-kraft.md)
  * [ADR-004: Chiến lược Lưu trữ Đa tầng (Redis + MinIO)](./docs/decisions/ADR-004-multi-tier-caching-and-object-storage.md)
  * [ADR-005: Tích hợp Sổ cái & Core Banking với Apache Fineract](./docs/decisions/ADR-005-apache-fineract-ledger-integration.md)
* 📡 **[API Documentation](./docs/API_DOCUMENTATION.md)**: Danh sách đầy đủ các Endpoint REST, Request Body, Response Code và cURL mẫu.
