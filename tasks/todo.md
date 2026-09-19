# Task List: Tái cấu trúc Order Query & Tích hợp vào graphQL-service

## 1. Hoàn nguyên Spring Boot Service (`erp_springboot-experiment`)
- [x] Loại bỏ `spring-boot-starter-graphql` khỏi `pom.xml`.
- [x] Loại bỏ cấu hình GraphiQL trong `config/application-dev.yml`.
- [x] Loại bỏ các matcher `/graphql` và `/graphiql/**` trong `SecurityConfiguration.java`.
- [x] Xóa bỏ các file GraphQL schema và controller tạm thời trong repo Spring Boot.
- [x] Giữ nguyên tính năng tìm kiếm đa trạng thái (`orderStatuses`) trong `OrderSearchRequest` và `OrderSpecification` để phục vụ REST và GraphQL Gateway.
- [x] Dự án biên dịch thành công 100%: `./mvnw compile -DskipTests`.

---

## 2. Triển khai GraphQL Order tại `graphQL-service` (`/home/ddicgegd/Projects/graphQL-service`)
- [x] Định nghĩa đầy đủ Types và Enums trong `src/modules/orders/types.js`:
  - `OrderDto` (đầy đủ thông tin chi tiết đơn hàng, món hàng `orderItems`, địa chỉ, người nhận, ngày tạo/hủy/hoàn thành).
  - `OrderSearchInput` (hỗ trợ tìm kiếm theo từ khóa, số đơn, trạng thái đơn, danh sách trạng thái, khoảng ngày, khoảng tiền, phân trang, sắp xếp).
  - `OrderStatisticsData` (thống kê doanh thu, tổng số đơn, cơ cấu theo trạng thái).
- [x] Xây dựng Resolvers trong `src/modules/orders/resolvers.js`:
  - `searchOrders(filter)` -> Proxy gọi `POST /api/orders/search` (xử lý offset paging và fallback).
  - `orderDetail(orderNumber)` -> Proxy tra cứu đơn hàng (hỗ trợ fallback search cho admin).
  - `myOrdersList(status, page, size, sortBy, sortDirection)` -> Proxy gọi `GET /api/orders/my-orders/list`.
  - `myOrderDetail(orderNumber)` -> Proxy gọi `GET /api/orders/my-orders/{orderNumber}`.
  - `pendingOrders` -> Proxy gọi `GET /api/orders/pending`.
  - `inProgressOrders` -> Proxy gọi `GET /api/orders/in-progress`.
  - `orderStatistics(startDate, endDate)` -> Proxy gọi `GET /api/orders/statistics` (tự động điền ngày mặc định an toàn).
  - `createOrder(input)` -> Mutation tạo đơn hàng.
  - `cancelOrder(orderId, cancellationReason)` -> Mutation hủy đơn hàng.
- [x] Khởi chạy dịch vụ background daemon trên port 4000 (`http://localhost:4000/graphql`).

---

## 3. Kiểm thử tự động End-to-End & Báo cáo kết quả
- [x] Test phân quyền: Xác thực unauthenticated request bị từ chối 403 an toàn.
- [x] Test `searchOrders`: Phân trang 3 items/page, lọc theo `orderStatuses: [WAITING_PAYMENT]` và keyword thành công.
- [x] Test `orderDetail`: Lấy đầy đủ thông tin đơn hàng và `orderItems`.
- [x] Test `pendingOrders`: Trả về danh sách 30 đơn hàng chờ.
- [x] Test `inProgressOrders`: Trả về 4 đơn hàng đang xử lý.
- [x] Test `orderStatistics`: Trả về dữ liệu thống kê thành công không bị lỗi thiếu tham số.
- [x] Test `createOrder`: Tạo đơn hàng mới thành công (`01a0b558-390b-7dd9-aee7-ef6287c9e546`).
- [x] Test `myOrdersList` & `myOrderDetail`: Đơn hàng mới xuất hiện và hiển thị chi tiết chính xác.
- [x] Test `cancelOrder`: Hủy đơn hàng và chuyển trạng thái sang `CANCELLED` thành công.
- [x] Báo cáo chi tiết gửi người dùng.
