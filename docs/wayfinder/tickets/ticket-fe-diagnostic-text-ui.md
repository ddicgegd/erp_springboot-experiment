# ticket-fe-diagnostic-text-ui

**Label:** `wayfinder:prototype`  
**Parent Map:** `map-order-fulfillment-diagnostic`  
**Status:** Open  
**Assignee:** Unassigned  
**Blocked by:** [ticket-api-contract-order-journey]

---

## Question

Thành phần giao diện dòng thời gian (`ProfilePage.tsx` / `OrderStatusStepper`) cần refactor hệ thống typography, màu sắc quang học mờ (`text-slate-500 font-normal` vs `text-slate-400 font-light`, optical bevel backdrop blur, active glow halo ring) và render động các đoạn chữ chẩn đoán (`step.desc`) như thế nào để đảm bảo tính thẩm mỹ chuẩn xác theo mockup Image #1 khi loại bỏ hàm mock `generateDeliveryStepsForOrder(...)` và nhận payload thực từ API Backend? Cần xử lý các trạng thái biên (text quá dài, thiếu timestamp, lỗi trạng thái hủy) như thế nào?
