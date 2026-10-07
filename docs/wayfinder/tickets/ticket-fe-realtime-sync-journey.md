# ticket-fe-realtime-sync-journey

**Label:** `wayfinder:research`  
**Parent Map:** `map-order-fulfillment-diagnostic`  
**Status:** Closed  
**Assignee:** scout (researchRealtimeSync)  
**Blocked by:** []

---

## Question

Cơ chế đồng bộ dữ liệu ở Frontend (React Query background refetching, polling interval theo chu kỳ trạng thái xử lý, hay tích hợp WebSocket subscription qua `websocket-service`) cần được thiết kế ra sao để cập nhật tức thời chữ chẩn đoán trạng thái (diagnostic subtext) và hiệu ứng phát sáng mốc tiến trình (`active step halo ring`) khi đơn hàng chuyển biến trạng thái tại kho/vận chuyển, đảm bảo zero layout jitter và tối ưu hiệu năng render?

---

## Research Resolution & Findings

1. **Hiện trạng hạ tầng WebSocket hiện có:**
   - Dịch vụ `websocket-service` (FastAPI) hiện tại được cấu hình `SESSION_TIMEOUT_SECONDS = 30` với mã đóng `4008` (Lifetime Expired). Đây là thiết kế chuyên biệt cho phiên thanh toán nhanh (Checkout Handshake), **không phù hợp** để duy trì kết nối theo dõi hành trình đơn hàng dài hạn (vài ngày).
   - `websocketService.ts` ở frontend tự động gọi `session.close(1000)` ngay khi nhận kết quả thanh toán.

2. **Giải pháp kiến trúc đồng bộ tối ưu (Hybrid Adaptive Strategy):**
   - **Tầng 1 (Primary - Immediate): TanStack React Query v5 Adaptive Polling:**
     - Sử dụng dynamic `refetchInterval` dựa trên `order.currentStatus`:
       + `PENDING` / `WAITING_PAYMENT`: 5,000ms (5 giây).
       + `PROCESSING` / `PACKED`: 15,000ms (15 giây).
       + `SHIPPING`: 30,000ms (30 giây).
       + `DELIVERED` / `COMPLETED` / `CANCELLED`: `false` (dừng polling hoàn toàn).
     - Kết hợp `refetchOnWindowFocus: true` và `staleTime: 5000` để đảm bảo dữ liệu mới nhất khi khách hàng quay lại tab.
   - **Tầng 2 (Long-term Scalability): Dedicated Long-Lived WebSocket / SSE Topic:**
     - Thiết lập channel chuyên biệt: `orders:tracking:{orderNumber}` tách biệt hoàn toàn khỏi channel thanh toán 30s.

3. **Kỹ thuật chống rung giật giao diện (Zero UI Jitter & Layout Stability):**
   - **Framer Motion Stable Keys:** Dùng `key={`${step.stage}-${step.timestamp}`}` thay vì index mảng `idx`, ngăn chặn việc unmount/remount toàn bộ cây DOM.
   - **Layout Reservation & Min-height:** Cố định `min-h-[64px]` cho mỗi card bước stepper; chữ chẩn đoán mờ áp dụng `line-clamp-2` kết hợp `transition-all duration-300` khi cập nhật text.
   - **Scroll Lock / Suppression:** Khi polling nền cập nhật chữ chẩn đoán, không reset vị trí thanh cuộn `stepsContainerRef`.
