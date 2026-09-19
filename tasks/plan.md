# Implementation Plan: Tái cấu trúc Order API & Chuyển đổi Truy vấn sang GraphQL

## Overview
Dự án tái cấu trúc toàn diện module Order nhằm giải quyết tình trạng bùng nổ endpoint dư thừa (endpoint sprawl), khắc phục sự chồng chéo giữa Customer và Admin, và áp dụng mô hình CQRS (Command Query Responsibility Segregation):
- **Phía Query (Đọc & Tìm kiếm):** Tích hợp Spring Boot Starter for GraphQL. Gom toàn bộ 6 endpoint đọc phân tán (`/my-orders/list`, `/my-orders/{orderNumber}`, `/search`, `/pending`, `/in-progress`, `/statistics`) vào 2-3 GraphQL queries linh hoạt, tái sử dụng `OrderSpecification` hiện có.
- **Phía Command (Ghi & Chuyển trạng thái):** Chuẩn hóa REST API theo resource-based (`/api/orders/{orderNumber}/...`), dọn dẹp các endpoint RPC rời rạc (`/confirm`, `/complete`, `/cancel`, `/ship`, `/transition`) thành bộ endpoint chuyển trạng thái thống nhất.

## Architecture Decisions
1. **Áp dụng CQRS (Command Query Responsibility Segregation):**
   - **GraphQL cho Query:** Toàn bộ truy vấn danh sách, chi tiết, lọc nâng cao, thống kê chuyển qua GraphQL (`/graphql`).
   - **REST cho Command:** Giữ các thao tác ghi dữ liệu (tạo đơn, cập nhật địa chỉ giao hàng, chuyển trạng thái) trên RESTful endpoints để tận dụng HTTP status codes, idempotency và xử lý transaction an toàn.
2. **Bảo mật & Phân quyền tầng DataFetcher:**
   - Bảo mật GraphQL thông qua Spring Security context (`SecurityUtil`).
   - Nếu caller là khách hàng (Role CUSTOMER): Tự động gán `customerId = currentUser.id` để ngăn chặn rò rỉ dữ liệu.
   - Nếu caller là Quản trị viên (Role ADMIN / STAFF): Cho phép truy vấn tất cả hoặc lọc theo bất kỳ khách hàng nào.
3. **Tái sử dụng JPA Specification:**
   - Không viết lại logic query DB. Ánh xạ `OrderFilterInput` trong GraphQL sang trực tiếp `OrderSpecification` có sẵn.
4. **Chiến lược Migration:**
   - Không xóa đột ngột các REST endpoint cũ ngay lập tức; đánh dấu `@Deprecated` trong Controller trong giai đoạn chuyển giao để tránh gãy giao diện hiện có, sau đó dọn dẹp hoàn toàn.

## Task List

### Phase 1: Nền tảng GraphQL & Cấu hình Security (Foundation)
- [ ] Task 1: Tích hợp `spring-boot-starter-graphql` vào `pom.xml` và cấu hình Spring Security cho endpoint `/graphql`
- [ ] Task 2: Định nghĩa Schema GraphQL cho Order (`src/main/resources/graphql/order.graphqls`)

### Checkpoint: Foundation
- [ ] Ứng dụng build thành công với Spring Boot GraphQL starter
- [ ] Endpoint `/graphql` hoạt động và truy cập được qua GraphiQL / Postman với JWT Authentication

### Phase 2: Triển khai Query DataFetchers (GraphQL Read Side)
- [ ] Task 3: Triển khai `OrderGraphQLQueryController` với query `orders(filter, page, sort)` tích hợp `OrderSpecification`
- [ ] Task 4: Triển khai query `order(orderNumber)` lấy chi tiết đơn hàng kèm phân quyền dữ liệu theo người dùng
- [ ] Task 5: Triển khai query `orderStatistics(startDate, endDate)` cho trang thống kê Admin

### Checkpoint: Core GraphQL Queries
- [ ] Khách hàng query được danh sách và chi tiết đơn hàng của chính mình
- [ ] Admin query được danh sách đơn hàng theo nhiều tiêu chí (`status = PENDING`, `PROCESSING`, khoảng ngày, số tiền)
- [ ] Admin query được thống kê đơn hàng qua GraphQL

### Phase 3: Chuẩn hóa & Tối ưu REST API Command Side
- [ ] Task 6: Chuẩn hóa các action cập nhật trạng thái đơn hàng (gom `/confirm`, `/complete`, `/transition` thành `PATCH /api/orders/{orderNumber}/status`)
- [ ] Task 7: Đánh dấu `@Deprecated` các REST Query endpoints cũ trong `OrderController` và chuẩn hóa route RESTful

### Checkpoint: Complete
- [ ] Toàn bộ luồng tạo, cập nhật, hủy đơn hoạt động thông suốt qua REST chuẩn hóa
- [ ] Toàn bộ luồng tìm kiếm, chi tiết, thống kê hoạt động tối ưu qua GraphQL
- [ ] Sẵn sàng đưa vào kiểm thử tích hợp

## Risks and Mitigations
| Risk | Impact | Mitigation |
| :--- | :--- | :--- |
| N+1 query problem khi fetch `orderItems` qua GraphQL | High | Sử dụng `@EntityGraph` hoặc JPA fetch join trong Repository khi truy vấn chi tiết đơn hàng |
| Client gửi query GraphQL lồng nhau quá sâu gây nghẽn CPU | Medium | Cấu hình `maxQueryDepth` giới hạn độ sâu query |
| Rò rỉ dữ liệu đơn hàng của khách hàng khác qua GraphQL | High | Kiểm tra quyền sở hữu bắt buộc tại tầng Service/Controller dựa trên `SecurityUtil.getCurrentUser()` |

## Open Questions
- Bạn muốn giữ GraphiQL UI bật trong môi trường dev (`spring.graphql.graphiql.enabled=true`) để test trực quan trên trình duyệt không?
- Bạn muốn giữ lại các endpoint REST GET cũ với tag `@Deprecated` trong bao lâu trước khi gỡ hẳn khỏi codebase?
