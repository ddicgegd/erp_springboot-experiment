# Map: Order Fulfillment Journey & Diagnostic Status Description Architecture (Hành trình Giao hàng & Chữ chẩn đoán trạng thái)

**Label:** `wayfinder:map`  
**Tracker:** local-markdown  
**Effort:** `map-order-fulfillment-diagnostic`

---

## Destination

Xây dựng bản đặc tả kiến trúc (Architectural Specification), hợp đồng dữ liệu chuẩn hóa (Data Contract DTO/REST API), mô hình hóa chuyển trạng thái nghiệp vụ (Fulfillment Event State Machine & Audit History) ở Backend Spring Boot, và hệ thống thành phần giao diện Frontend (Design System Typography, Optical Bevel Card, Active Ambient Pulse & Dynamic Subtext) cho "Hành trình giao hàng & Chữ chẩn đoán mờ (Diagnostic Status Description)".

Đích đến của nỗ lực này đạt được khi:
1. Backend Spring Boot cung cấp mô hình lưu trữ lịch sử trạng thái giàu ngữ cảnh (timestamp, title, diagnostic text, actor/facility, tracking status) thay vì chỉ lưu enum phẳng `List<OrderStatus>` như hiện tại.
2. Endpoint RESTful `/api/v1/orders/my-orders/{orderNumber}` (hoặc chuyên biệt `/api/v1/orders/my-orders/{orderNumber}/journey`) trả về danh sách `deliverySteps` có đầy đủ `title`, `diagnosticDescription` (chữ chẩn đoán chi tiết), `timestamp`, `completed`, `active`, `carrierInfo`.
3. Frontend (ProfilePage/OrderTracking) loại bỏ triệt để logic hardcode client-side `generateDeliveryStepsForOrder(...)` để tiêu thụ trực tiếp dữ liệu chuẩn từ Backend, hiển thị mượt mà trạng thái mờ (text-slate-500/font-normal, optical bevel backdrop blur, glowing pulse ring) theo đúng chuẩn thiết kế trong Image #1.

---

## Notes

- **Domain:** E-Commerce / ERP Fulfillment, Order State Management, UI/UX Glassmorphism & Stepper Diagnostic Hierarchy.
- **Frontend Codebase:** `~/Projects/noname/src/components/ProfilePage.tsx`, `~/Projects/noname/src/services/orderService.ts`, `~/Projects/fe-erp_springboot-experiment`.
- **Backend Codebase:** `~/Projects/erp_springboot-experiment` (Spring Boot 3, Java 21, JPA/Hibernate, MapStruct, Lombok).
- **Core Entities & DTOs hiện tại:**
  - Backend: `Order.java` (chỉ có `@Column(name = "order_status") List<OrderStatus> status`), `MyOrderDetailResponse.java`, `OrderMapper.java`.
  - Frontend: `UiDeliveryStep { title, desc, time, completed, active }`, `ProfilePage.tsx` lines 2726–2935.
- **Non-Sequential Processing Algorithm:** Mọi ticket trừ trường hợp có quan hệ phụ thuộc tiên quyết cứng (`blocked-by`) đều được thực thi song song, độc lập thứ tự.

---

## Decisions so far

<!-- the index: list closed tickets with enriched context. -->

- [ticket-fe-realtime-sync-journey](tickets/ticket-fe-realtime-sync-journey.md): Nghiên cứu kiến trúc đồng bộ thời gian thực cho chữ chẩn đoán và hành trình đơn hàng. Quyết định áp dụng mô hình Hybrid: Giai đoạn 1 ưu tiên Adaptive Polling qua TanStack React Query (5s khi PENDING/WAITING_PAYMENT, 15s khi PROCESSING/PACKED, 30s khi SHIPPING, tắt khi DELIVERED/COMPLETED) kết hợp kỹ thuật triệt tiêu rung giật UI (Framer Motion stable key, min-height reservation, scroll suppression). Không tái sử dụng socket thanh toán ngắn hạn 30s (`websocket-service`) cho hành trình đơn hàng dài hạn.

---

## Not yet specified

<!-- Fog of war: in-scope fog you can't ticket yet; graduates as the frontier advances -->

- Tích hợp Webhook tiếp nhận sự kiện lộ trình trực tiếp từ API đối tác chuyển phát nhanh Viettel Post / VNPost / GHTK.
- Cơ chế phân quyền hiển thị thông điệp chẩn đoán nhạy cảm (Customer-facing diagnostic description vs Internal warehouse inspection log).
- Chính sách lưu trữ đệm phân tầng Redis Cache cho Timeline Journey của các đơn hàng có tần suất truy vấn cao (Hot Orders).

---

## Out of scope

- Tích hợp trực tiếp cổng thanh toán bên thứ ba mới ngoài VNPAY/Google Pay đã có.
- Viết lại toàn bộ trang Checkout hoặc Shopping Cart.
- Thay đổi hệ thống Database từ Oracle/Postgres sang NoSQL.
